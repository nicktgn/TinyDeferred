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

package com.github.nicktgn.deferred;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

public class Deferred<T>{

	private HashMap<Deferred, Boolean> whenRefs;
	private HashMap<Deferred, List<Integer>> whenPromisesRefs;
	private List<Object> whenResults;
	private int whenTotalCount;
	private int whenFailCount;

	private List<Promise<T>> mPromises = null;
	
	private boolean mIsRejected = false;
	private boolean mIsResolved = false;
	
	private boolean mIsMultiReject = false;
	private boolean mIsMultiResolve = false;
	
	private T mResult = null;
	private Exception mErr = null;
	
	private MainThreadHandler mtHandler = null;
	
	MainThreadHandler getMainThreadHandler(){
		if(mtHandler == null){
			mtHandler = new MainThreadHandler();
		}
		return mtHandler;
	}
	
	T getResult(){
		return mResult;
	}
	Exception getError(){
		return mErr;
	}
	
	public boolean isRejected(){
		return mIsRejected;
	}
	
	public boolean isResolved(){
		return mIsResolved;
	}
	
	public boolean isMultiResolve(){
		return mIsMultiResolve;
	}
	
	public boolean isMultiReject(){
		return mIsMultiReject;
	}
	
	public void reject(Exception err){
		mIsMultiReject = false;
		if((mIsResolved && !mIsMultiResolve) || (mIsRejected && !mIsMultiReject)) return;
		
		mErr = err;
		if(mPromises != null){
			for(Iterator<Promise<T>> it = mPromises.iterator(); it.hasNext();){
				it.next().reject(err, this);
				if(!mIsMultiReject && !mIsMultiResolve) it.remove();
			}
		}
			
		mIsRejected = true;
	}
	
	public void reject(Exception err, boolean multiReject){
		mIsMultiReject = multiReject;
		if((mIsResolved && !mIsMultiResolve) || (mIsRejected && !mIsMultiReject)) return;
		
		mErr = err;
		if(mPromises != null){
			for(Iterator<Promise<T>> it = mPromises.iterator(); it.hasNext();){
				it.next().reject(err, this);
				if(!mIsMultiReject && !mIsMultiResolve) it.remove();
			}
		}
		mIsRejected = true;
	}
	
	public void resolve(T result){
		mIsMultiResolve = false;
		if((mIsResolved && !mIsMultiResolve) || (mIsRejected && !mIsMultiReject)) return;
		
		mResult = result;
		if(mPromises != null){
			for(Iterator<Promise<T>> it = mPromises.iterator(); it.hasNext();){
				it.next().resolve(result, this);
				if(!mIsMultiReject && !mIsMultiResolve) it.remove();
			}
		}
		mIsResolved = true;
	}
	
	public void resolve(T result, boolean multiResolve){
		mIsMultiResolve = multiResolve;
		if((mIsResolved && !mIsMultiResolve) || (mIsRejected && !mIsMultiReject)) return;
		
		mResult = result;
		if(mPromises != null){
			for(Iterator<Promise<T>> it = mPromises.iterator(); it.hasNext();){
				it.next().resolve(result, this);
				if(!mIsMultiReject && !mIsMultiResolve) it.remove();
			}
		}
		mIsResolved = true;
	}
	
	public Promise<T> promise(){
		if(mPromises == null){
			mPromises = new ArrayList<Promise<T>>();
		}
		Promise<T> p = new Promise<T>(this);
		mPromises.add(p);
		return p;
	}

	public static Promise<List> when(final Promise... promises) {
		final Deferred<List> whenDeferred = new Deferred<List>();

		whenDeferred.whenRefs = new HashMap<Deferred, Boolean>();
		whenDeferred.whenPromisesRefs = new HashMap<Deferred, List<Integer>>();
		whenDeferred.whenResults = new ArrayList<Object>();
		whenDeferred.whenTotalCount = whenDeferred.whenFailCount = 0;

		final Promise<Object> counterPromise = new Promise<Object>(null);
		counterPromise.then(new Promise.Then<Object>() {
		   @Override
		   public void onThen(Object result, Exception e) {
				List<Integer> indexes = whenDeferred.whenPromisesRefs.get(this.deferred);
				for(Integer i: indexes){
					whenDeferred.whenResults.set(i, (result != null ? result : e));
				}

				if(whenDeferred.whenRefs.get(this.deferred) == false){
					whenDeferred.whenRefs.put(this.deferred, true);
					whenDeferred.whenTotalCount++;
				   if(e != null) whenDeferred.whenFailCount++;
					if(whenDeferred.whenTotalCount == whenDeferred.whenRefs.size()){
						if(whenDeferred.whenFailCount == whenDeferred.whenTotalCount){
							whenDeferred.reject(new Exception("All of the promises failed"));
						}
						else{
							whenDeferred.resolve(whenDeferred.whenResults);
						}
					}
			   }
		   }
	   });

		int i = 0;
		for (Promise p : promises) {
			if (!whenDeferred.whenRefs.containsKey(p.mDeferred)) {
				whenDeferred.whenRefs.put(p.mDeferred, false);
			}

			List<Integer> indexes = whenDeferred.whenPromisesRefs.get(p.mDeferred);
			if(indexes == null){
				indexes = new ArrayList<Integer>();
				whenDeferred.whenPromisesRefs.put(p.mDeferred, indexes);
			}
			indexes.add(i);

			p.mDeferred.mPromises.add(counterPromise);

			i++;
		}

		return whenDeferred.promise();
	}
	
	static class Promised{
		public Promise.DoneOnMainThread doneCb;
		public Object result;
		public Promise.FailOnMainThread failCb;
		public Exception error;
		public Promise.ThenOnMainThread thenCb;
	}
	
	
	static class MainThreadHandler extends Handler{
		private LinkedList<Promised> queue = new LinkedList<Promised>();
		
		public MainThreadHandler(){
			super(Looper.getMainLooper());
		}
		
		@Override
		public void handleMessage(Message msg){
			while(true){
				synchronized(this){
					Promised p = queue.poll();
					if(p == null){
						return;
					}
					
					if(p.doneCb != null){
						p.doneCb.onDone(p.result);
					}
					else if(p.failCb != null){
						p.failCb.onFail(p.error);
					}
					else if(p.thenCb != null){
						p.thenCb.onThen(p.result, p.error);
					}
				}
			}
		}
		
		public void sendToMainThread(Promise.DoneOnMainThread doneCb, Object result){
			synchronized(this){
				Promised p = new Promised();
				p.doneCb = doneCb;
				p.result = result;
				queue.add(p);
				
				sendMessage(obtainMessage());
			}
		}
		
		public void sendToMainThread(Promise.FailOnMainThread failCb, Exception error){
			synchronized(this){
				Promised p = new Promised();
				p.failCb = failCb;
				p.error = error;
				queue.add(p);
				
				sendMessage(obtainMessage());
			}
		}

		public void sendToMainThread(Promise.ThenOnMainThread thenCb, Object result, Exception error){
			synchronized(this){
				Promised p = new Promised();
				p.thenCb = thenCb;
				p.result = result;
				p.error = error;
				queue.add(p);

				sendMessage(obtainMessage());
			}
		}
	}
	
}
