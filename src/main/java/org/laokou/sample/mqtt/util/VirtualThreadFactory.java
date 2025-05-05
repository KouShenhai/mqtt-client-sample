/*
 * Copyright (c) 2022-2025 KCloud-Platform-IoT Author or Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.laokou.sample.mqtt.util;

import org.springframework.lang.NonNull;

import java.util.concurrent.ThreadFactory;

/**
 * 自定义线程工厂【虚拟】.
 *
 * @author laokou
 */
final class VirtualThreadFactory implements ThreadFactory {

	public static final VirtualThreadFactory INSTANCE = new VirtualThreadFactory();

	@Override
	public Thread newThread(@NonNull Runnable r) {
		Thread thread = new Thread(r);
		return Thread.ofVirtual()
			.name("laokou-virtual-" + thread.getName())
			.inheritInheritableThreadLocals(true)
			.unstarted(r);
	}

}
