/*
 * Copyright 2006-2007 the original author or authors.
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

package com.github.wbw.retry.policy;

import com.github.wbw.retry.RetryContext;
import com.github.wbw.retry.RetryPolicy;
import com.github.wbw.retry.context.RetryContextSupport;

/**
 * A {@link RetryPolicy} that allows the first attempt but never permits a retry. Also be
 * used as a base class for other policies, e.g. for test purposes as a stub.
 *
 * @author Dave Syer
 *
 */
@SuppressWarnings("serial")
public class NeverRetryPolicy implements RetryPolicy {

	/**
	 * Returns false after the first exception. So there is always one try, and then the
	 * retry is prevented.
	 *
	 * @see com.github.wbw.retry.RetryPolicy#canRetry(com.github.wbw.retry.RetryContext)
	 */
	public boolean canRetry(RetryContext context) {
		return !((NeverRetryContext) context).isFinished();
	}

	/**
	 * Do nothing.
	 *
	 * @see com.github.wbw.retry.RetryPolicy#close(com.github.wbw.retry.RetryContext)
	 */
	public void close(RetryContext context) {
		// no-op
	}

	/**
	 * Return a context that can respond to early termination requests, but does nothing
	 * else.
	 *
	 * @see com.github.wbw.retry.RetryPolicy#open(RetryContext)
	 */
	public RetryContext open(RetryContext parent) {
		return new NeverRetryContext(parent);
	}

	/**
	 * Make the throwable available for downstream use through the context.
	 * @see com.github.wbw.retry.RetryPolicy#registerThrowable(com.github.wbw.retry.RetryContext,
	 * Throwable)
	 */
	public void registerThrowable(RetryContext context, Throwable throwable) {
		((NeverRetryContext) context).setFinished();
		((RetryContextSupport) context).registerThrowable(throwable);
	}

	/**
	 * Special context object for {@link NeverRetryPolicy}. Implements a flag with a
	 * similar function to {@link RetryContext#isExhaustedOnly()}, but kept separate so
	 * that if subclasses of {@link NeverRetryPolicy} need to they can modify the
	 * behaviour of {@link NeverRetryPolicy#canRetry(RetryContext)} without affecting
	 * {@link RetryContext#isExhaustedOnly()}.
	 *
	 * @author Dave Syer
	 *
	 */
	private static class NeverRetryContext extends RetryContextSupport {

		private boolean finished = false;

		public NeverRetryContext(RetryContext parent) {
			super(parent);
		}

		public boolean isFinished() {
			return finished;
		}

		public void setFinished() {
			this.finished = true;
		}

	}

}
