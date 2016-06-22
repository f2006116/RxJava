/**
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package rx.internal.operators;

import rx.*;
import rx.Observable.Operator;
import rx.exceptions.*;
import rx.functions.*;
import rx.internal.util.RxJavaPluginUtils;

/**
 * An {@link Operator} that pairs up items emitted by a source {@link Observable} with the sequence of items
 * emitted by the {@code Observable} that is derived from each item by means of a selector, and emits the
 * results of this pairing.
 *
 * @param <T>
 *            the type of items emitted by the source {@code Observable}
 * @param <U>
 *            the type of items emitted by the derived {@code Observable}s
 * @param <R>
 *            the type of items to be emitted by this {@code Operator}
 */
public final class OperatorMapPair<T, U, R> implements Operator<Observable<? extends R>, T> {

    /**
     * Creates the function that generates a {@code Observable} based on an item emitted by another {@code Observable}.
     * 
     * @param <T> the input value type
     * @param <U> the value type of the generated Observable
     * @param selector
     *            a function that accepts an item and returns an {@code Iterable} of corresponding items
     * @return a function that converts an item emitted by the source {@code Observable} into an {@code Observable} that emits the items generated by {@code selector} operating on that item
     */
    public static <T, U> Func1<T, Observable<U>> convertSelector(final Func1<? super T, ? extends Iterable<? extends U>> selector) {
        return new Func1<T, Observable<U>>() {
            @SuppressWarnings("cast")
            @Override
            public Observable<U> call(T t1) {
                return (Observable<U>)Observable.from(selector.call(t1));
            }
        };
    }

    final Func1<? super T, ? extends Observable<? extends U>> collectionSelector;
    final Func2<? super T, ? super U, ? extends R> resultSelector;

    public OperatorMapPair(final Func1<? super T, ? extends Observable<? extends U>> collectionSelector, final Func2<? super T, ? super U, ? extends R> resultSelector) {
        this.collectionSelector = collectionSelector;
        this.resultSelector = resultSelector;
    }

    @Override
    public Subscriber<? super T> call(final Subscriber<? super Observable<? extends R>> o) {
        MapPairSubscriber<T, U, R> parent = new MapPairSubscriber<T, U, R>(o, collectionSelector, resultSelector);
        o.add(parent);
        return parent;
    }
    
    static final class MapPairSubscriber<T, U, R> extends Subscriber<T> {
        
        final Subscriber<? super Observable<? extends R>> actual;
        
        final Func1<? super T, ? extends Observable<? extends U>> collectionSelector;
        final Func2<? super T, ? super U, ? extends R> resultSelector;

        boolean done;
        
        public MapPairSubscriber(Subscriber<? super Observable<? extends R>> actual, 
                Func1<? super T, ? extends Observable<? extends U>> collectionSelector,
                        Func2<? super T, ? super U, ? extends R> resultSelector) {
            this.actual = actual;
            this.collectionSelector = collectionSelector;
            this.resultSelector = resultSelector;
        }
        
        @Override
        public void onNext(T outer) {
            
            Observable<? extends U> intermediate;
            
            try {
                intermediate = collectionSelector.call(outer);
            } catch (Throwable ex) {
                Exceptions.throwIfFatal(ex);
                unsubscribe();
                onError(OnErrorThrowable.addValueAsLastCause(ex, outer));
                return;
            }
            
            actual.onNext(intermediate.map(new OuterInnerMapper<T, U, R>(outer, resultSelector)));
        }
        
        @Override
        public void onError(Throwable e) {
            if (done) {
                RxJavaPluginUtils.handleException(e);
                return;
            }
            done = true;
            
            actual.onError(e);
        }
        
        
        @Override
        public void onCompleted() {
            if (done) {
                return;
            }
            actual.onCompleted();
        }
        
        @Override
        public void setProducer(Producer p) {
            actual.setProducer(p);
        }
    }

    static final class OuterInnerMapper<T, U, R> implements Func1<U, R> {
        final T outer;
        final Func2<? super T, ? super U, ? extends R> resultSelector;

        public OuterInnerMapper(T outer, Func2<? super T, ? super U, ? extends R> resultSelector) {
            this.outer = outer;
            this.resultSelector = resultSelector;
        }
        
        @Override
        public R call(U inner) {
            return resultSelector.call(outer, inner);
        }
       
    }
}