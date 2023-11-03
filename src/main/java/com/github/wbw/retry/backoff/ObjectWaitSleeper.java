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
package com.github.wbw.retry.backoff;

/**
 * Simple {@link Sleeper} implementation that just waits on a local Object.
 *
 * @deprecated in favor of {@link com.github.wbw.retry.backoff.ThreadWaitSleeper}
 * @author Dave Syer
 *
 */
@SuppressWarnings("serial")
@Deprecated
public class ObjectWaitSleeper implements Sleeper {

	/*
	 * (non-Javadoc)
	 *
	 * @see com.github.wbw.batch.retry.backoff.Sleeper#sleep(long)
	 */
	public void sleep(long backOffPeriod) throws InterruptedException {
		Object mutex = new Object();
		synchronized (mutex) {
			mutex.wait(backOffPeriod);
		}
	}

}
