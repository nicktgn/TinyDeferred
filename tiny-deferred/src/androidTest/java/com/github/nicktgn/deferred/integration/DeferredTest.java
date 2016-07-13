/*
 * Copyright (c) 2016 Nick Tsygankov (nicktgn@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.nicktgn.deferred.integration;

import android.support.test.runner.AndroidJUnit4;
import android.support.test.runner.AndroidJUnitRunner;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;

import com.github.nicktgn.deferred.Deferred;
import com.github.nicktgn.deferred.Promise;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Timer;
import java.util.TimerTask;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(AndroidJUnit4.class)
public class DeferredTest{

	public Promise<Boolean> asyncResult(){
		final Deferred<Boolean> d1 = new Deferred<>();

		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep(300);
					d1.resolve(true);
				} catch (InterruptedException e) {
					d1.reject(new Exception("Failed"));
					e.printStackTrace();
				}
			}
		}).start();

		return d1.promise();
	}


	@Test
	public void doneTest() {

		final boolean[] r1 = {false};

		System.out.println("Deferred Test: before timer");

		asyncResult().then(new Promise.Then<Boolean>() {
			@Override
			public void onThen(Boolean result, Exception e) {
				System.out.println("Deferred Test: after timer: " + result);
				r1[0] = result;
			}
		});

		try {
			Thread.sleep(400);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		assertThat(r1[0]).isTrue();

	}

}
