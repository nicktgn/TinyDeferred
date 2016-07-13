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

public class Promise<T> {

	Deferred<T> mDeferred;

	private ArrayList<Done<T>> doneCb = new ArrayList<Done<T>>();
	private ArrayList<Fail> failCb = new ArrayList<Fail>();
	private ArrayList<Then<T>> thenCb = new ArrayList<Then<T>>();
	
	Promise(Deferred<T> deferred){
		mDeferred = deferred;
	}

	Deferred mResolvedDeferred;
	Deferred mRejectedDeferred;

	void resolve(T result, Deferred resolvedDeferred){
		mResolvedDeferred = resolvedDeferred;
		for(Done<T> cb : doneCb){
			cb._done(this, result);
		}
		for(Then<T> cb : thenCb){
			cb._setDeferred(resolvedDeferred);
			cb._then(this, result, null);
		}
	}
	
	void reject(Exception e, Deferred rejectedDeferred){
		mRejectedDeferred = rejectedDeferred;
		for(Fail cb : failCb){
			cb._fail(this, e);
		}
		for(Then<T> cb : thenCb){
			cb._setDeferred(rejectedDeferred);
			cb._then(this, null, e);
		}
	}
	
	public boolean isRejected(){
		if(mDeferred == null) return false;
		return mDeferred.isRejected();
	}
	
	public boolean isResolved(){
		if(mDeferred == null) return false;
		return mDeferred.isResolved();
	}
	
	public boolean isMultiResolve(){
		if(mDeferred == null) return false;
		return mDeferred.isMultiResolve();
	}
	
	public boolean isMultiReject(){
		if(mDeferred == null) return false;
		return mDeferred.isMultiReject();
	}

	/*
	public Promise<T> then(final Deferred<T> d){
		this.fail(new Fail(){
			@Override
			public void onFail(Exception e) {
				d.reject(e);
			}
		})
		.done(new Done<T>() {
			@Override
			public void onDone(T result) {
				d.resolve(result);
			}
		});
		
		return this;
	}*/
	
	public Promise<T> done(Done<T> cb){
		if(isResolved()){
			cb._done(this, mDeferred.getResult());
		}
		else{
			doneCb.add(cb);
		}
		return this;
	}
	
	public Promise<T> fail(Fail cb){
		if(isRejected()){
			cb._fail(this, mDeferred.getError());
		}
		else{
			failCb.add(cb);
		}
		return this;
	}

	public Promise<T> then(Then<T> cb){
		if(isRejected() || isResolved()){
			cb._then(this, mDeferred.getResult(), mDeferred.getError());
		}
		else{
			thenCb.add(cb);
		}
		return this;
	}
	
	public abstract static class Done<T>{
		abstract public void onDone(T result);
		void _done(Promise<T> p, T result){
			onDone(result);
		}
	}
	public abstract static class Fail{
		abstract public void onFail(Exception e);
		void _fail(Promise p, Exception e){
			onFail(e);
		}
	}

	public abstract static class Then<T>{
		abstract public void onThen(T result, Exception e);
		void _then(Promise<T> p, T result, Exception e){
			onThen(result, e);
		}
		protected Deferred deferred;
		void _setDeferred(Deferred deferred){
			this.deferred = deferred;
		}
	}
	
	public abstract static class DoneOnMainThread<T> extends Done<T>{
		void _done(Promise<T> p, T result){
			p.mDeferred.getMainThreadHandler().sendToMainThread(this, result);
		}
	}
	
	public abstract static class FailOnMainThread extends Fail{
		void _fail(Promise p, Exception e){
			p.mDeferred.getMainThreadHandler().sendToMainThread(this, e);
		}
	}

	public abstract static class ThenOnMainThread<T> extends Then<T>{
		void _fail(Promise<T> p, T result, Exception e){
			p.mDeferred.getMainThreadHandler().sendToMainThread(this, result, e);
		}
	}
	
}
