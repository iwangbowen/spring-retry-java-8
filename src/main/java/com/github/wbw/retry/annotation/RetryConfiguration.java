/*
 * Copyright 2006-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.wbw.retry.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.aopalliance.aop.Advice;

import com.github.wbw.aop.ClassFilter;
import com.github.wbw.aop.IntroductionAdvisor;
import com.github.wbw.aop.MethodMatcher;
import com.github.wbw.aop.Pointcut;
import com.github.wbw.aop.support.AbstractPointcutAdvisor;
import com.github.wbw.aop.support.ComposablePointcut;
import com.github.wbw.aop.support.StaticMethodMatcherPointcut;
import com.github.wbw.aop.support.annotation.AnnotationClassFilter;
import com.github.wbw.aop.support.annotation.AnnotationMethodMatcher;
import com.github.wbw.beans.factory.BeanFactory;
import com.github.wbw.beans.factory.BeanFactoryAware;
import com.github.wbw.beans.factory.InitializingBean;
import com.github.wbw.beans.factory.ListableBeanFactory;
import com.github.wbw.beans.factory.SmartInitializingSingleton;
import com.github.wbw.beans.factory.config.BeanDefinition;
import com.github.wbw.context.annotation.ImportAware;
import com.github.wbw.context.annotation.Role;
import com.github.wbw.core.OrderComparator;
import com.github.wbw.core.annotation.AnnotationAttributes;
import com.github.wbw.core.annotation.AnnotationUtils;
import com.github.wbw.core.type.AnnotationMetadata;
import com.github.wbw.lang.Nullable;
import com.github.wbw.retry.RetryListener;
import com.github.wbw.retry.backoff.Sleeper;
import com.github.wbw.retry.interceptor.MethodArgumentsKeyGenerator;
import com.github.wbw.retry.interceptor.NewMethodArgumentsIdentifier;
import com.github.wbw.retry.policy.RetryContextCache;
import com.github.wbw.stereotype.Component;
import com.github.wbw.util.ObjectUtils;
import com.github.wbw.util.ReflectionUtils;

/**
 * Basic configuration for <code>@Retryable</code> processing. For stateful retry, if
 * there is a unique bean elsewhere in the context of type {@link RetryContextCache},
 * {@link MethodArgumentsKeyGenerator} or {@link NewMethodArgumentsIdentifier} it will be
 * used by the corresponding retry interceptor (otherwise sensible defaults are adopted).
 *
 * @author Dave Syer
 * @author Artem Bilan
 * @author Markus Heiden
 * @author Gary Russell
 * @author Yanming Zhou
 * @since 1.1
 *
 */
@SuppressWarnings("serial")
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@Component
public class RetryConfiguration extends AbstractPointcutAdvisor
		implements IntroductionAdvisor, BeanFactoryAware, InitializingBean, SmartInitializingSingleton, ImportAware {

	@Nullable
	protected AnnotationAttributes enableRetry;

	private AnnotationAwareRetryOperationsInterceptor advice;

	private Pointcut pointcut;

	private RetryContextCache retryContextCache;

	private List<RetryListener> retryListeners;

	private MethodArgumentsKeyGenerator methodArgumentsKeyGenerator;

	private NewMethodArgumentsIdentifier newMethodArgumentsIdentifier;

	private Sleeper sleeper;

	private BeanFactory beanFactory;

	@Override
	public void setImportMetadata(AnnotationMetadata importMetadata) {
		this.enableRetry = AnnotationAttributes
			.fromMap(importMetadata.getAnnotationAttributes(EnableRetry.class.getName()));
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		this.retryContextCache = findBean(RetryContextCache.class);
		this.methodArgumentsKeyGenerator = findBean(MethodArgumentsKeyGenerator.class);
		this.newMethodArgumentsIdentifier = findBean(NewMethodArgumentsIdentifier.class);
		this.sleeper = findBean(Sleeper.class);
		Set<Class<? extends Annotation>> retryableAnnotationTypes = new LinkedHashSet<>(1);
		retryableAnnotationTypes.add(Retryable.class);
		this.pointcut = buildPointcut(retryableAnnotationTypes);
		this.advice = buildAdvice();
		this.advice.setBeanFactory(this.beanFactory);
		if (this.enableRetry != null) {
			setOrder(enableRetry.getNumber("order"));
		}
	}

	@Override
	public void afterSingletonsInstantiated() {
		this.retryListeners = findBeans(RetryListener.class);
		if (this.retryListeners != null) {
			this.advice.setListeners(this.retryListeners);
		}
	}

	private <T> List<T> findBeans(Class<? extends T> type) {
		if (this.beanFactory instanceof ListableBeanFactory) {
			ListableBeanFactory listable = (ListableBeanFactory) this.beanFactory;
			if (listable.getBeanNamesForType(type).length > 0) {
				ArrayList<T> list = new ArrayList<>(listable.getBeansOfType(type, false, false).values());
				OrderComparator.sort(list);
				return list;
			}
		}
		return null;
	}

	private <T> T findBean(Class<? extends T> type) {
		if (this.beanFactory instanceof ListableBeanFactory) {
			ListableBeanFactory listable = (ListableBeanFactory) this.beanFactory;
			if (listable.getBeanNamesForType(type, false, false).length == 1) {
				return listable.getBean(type);
			}
		}
		return null;
	}

	/**
	 * Set the {@code BeanFactory} to be used when looking up executors by qualifier.
	 */
	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		this.beanFactory = beanFactory;
	}

	@Override
	public ClassFilter getClassFilter() {
		return this.pointcut.getClassFilter();
	}

	@Override
	public Class<?>[] getInterfaces() {
		return new Class[] { com.github.wbw.retry.interceptor.Retryable.class };
	}

	@Override
	public void validateInterfaces() throws IllegalArgumentException {
	}

	@Override
	public Advice getAdvice() {
		return this.advice;
	}

	@Override
	public Pointcut getPointcut() {
		return this.pointcut;
	}

	protected AnnotationAwareRetryOperationsInterceptor buildAdvice() {
		AnnotationAwareRetryOperationsInterceptor interceptor = new AnnotationAwareRetryOperationsInterceptor();
		if (this.retryContextCache != null) {
			interceptor.setRetryContextCache(this.retryContextCache);
		}
		if (this.methodArgumentsKeyGenerator != null) {
			interceptor.setKeyGenerator(this.methodArgumentsKeyGenerator);
		}
		if (this.newMethodArgumentsIdentifier != null) {
			interceptor.setNewItemIdentifier(this.newMethodArgumentsIdentifier);
		}
		if (this.sleeper != null) {
			interceptor.setSleeper(this.sleeper);
		}
		return interceptor;
	}

	/**
	 * Calculate a pointcut for the given retry annotation types, if any.
	 * @param retryAnnotationTypes the retry annotation types to introspect
	 * @return the applicable Pointcut object, or {@code null} if none
	 */
	protected Pointcut buildPointcut(Set<Class<? extends Annotation>> retryAnnotationTypes) {
		ComposablePointcut result = null;
		for (Class<? extends Annotation> retryAnnotationType : retryAnnotationTypes) {
			Pointcut filter = new AnnotationClassOrMethodPointcut(retryAnnotationType);
			if (result == null) {
				result = new ComposablePointcut(filter);
			}
			else {
				result.union(filter);
			}
		}
		return result;
	}

	private final class AnnotationClassOrMethodPointcut extends StaticMethodMatcherPointcut {

		private final MethodMatcher methodResolver;

		AnnotationClassOrMethodPointcut(Class<? extends Annotation> annotationType) {
			this.methodResolver = new AnnotationMethodMatcher(annotationType);
			setClassFilter(new AnnotationClassOrMethodFilter(annotationType));
		}

		@Override
		public boolean matches(Method method, Class<?> targetClass) {
			return getClassFilter().matches(targetClass) || this.methodResolver.matches(method, targetClass);
		}

		@Override
		public boolean equals(Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof AnnotationClassOrMethodPointcut)) {
				return false;
			}
			AnnotationClassOrMethodPointcut otherAdvisor = (AnnotationClassOrMethodPointcut) other;
			return ObjectUtils.nullSafeEquals(this.methodResolver, otherAdvisor.methodResolver);
		}

	}

	private final class AnnotationClassOrMethodFilter extends AnnotationClassFilter {

		private final AnnotationMethodsResolver methodResolver;

		AnnotationClassOrMethodFilter(Class<? extends Annotation> annotationType) {
			super(annotationType, true);
			this.methodResolver = new AnnotationMethodsResolver(annotationType);
		}

		@Override
		public boolean matches(Class<?> clazz) {
			return super.matches(clazz) || this.methodResolver.hasAnnotatedMethods(clazz);
		}

	}

	private static class AnnotationMethodsResolver {

		private final Class<? extends Annotation> annotationType;

		public AnnotationMethodsResolver(Class<? extends Annotation> annotationType) {
			this.annotationType = annotationType;
		}

		public boolean hasAnnotatedMethods(Class<?> clazz) {
			final AtomicBoolean found = new AtomicBoolean(false);
			ReflectionUtils.doWithMethods(clazz, method -> {
				if (found.get()) {
					return;
				}
				Annotation annotation = AnnotationUtils.findAnnotation(method,
						AnnotationMethodsResolver.this.annotationType);
				if (annotation != null) {
					found.set(true);
				}
			});
			return found.get();
		}

	}

}
