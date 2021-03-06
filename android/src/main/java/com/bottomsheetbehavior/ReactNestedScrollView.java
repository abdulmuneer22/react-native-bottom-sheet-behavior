/**
 * Copyright (c) 2015-present, Facebook, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */

package com.bottomsheetbehavior;


import javax.annotation.Nullable;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.support.v4.widget.NestedScrollView;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ScrollView;

import com.facebook.react.uimanager.MeasureSpecAssertions;
import com.facebook.react.uimanager.events.NativeGestureUtil;
import com.facebook.react.views.scroll.OnScrollDispatchHelper;
import com.facebook.react.views.view.ReactClippingViewGroup;
import com.facebook.react.views.view.ReactClippingViewGroupHelper;
import com.facebook.infer.annotation.Assertions;

/**
 * Forked from https://github.com/facebook/react-native/blob/4498bc819730e3c513750a04d705883f1d61816d/ReactAndroid/src/main/java/com/facebook/react/views/scroll/ReactScrollView.java
 *
 * A simple subclass of ScrollView that doesn't dispatch measure and layout to its children and has
 * a scroll listener to send scroll events to JS.
 *
 * <p>ReactScrollView only supports vertical scrolling. For horizontal scrolling,
 * use {@link ReactHorizontalScrollView}.
 */
public class ReactNestedScrollView extends NestedScrollView implements ReactClippingViewGroup {

    private final OnScrollDispatchHelper mOnScrollDispatchHelper = new OnScrollDispatchHelper();

    private @Nullable Rect mClippingRect;
    private boolean mDoneFlinging;
    private boolean mDragging;
    private boolean mFlinging;
    private boolean mRemoveClippedSubviews;
    private boolean mScrollEnabled = true;
    private boolean mSendMomentumEvents;
    private @Nullable Drawable mEndBackground;
    private int mEndFillColor = Color.TRANSPARENT;

    public ReactNestedScrollView(Context context) {
        super(context);
    }

    public void setSendMomentumEvents(boolean sendMomentumEvents) {
        mSendMomentumEvents = sendMomentumEvents;
    }

    public void setScrollEnabled(boolean scrollEnabled) {
        mScrollEnabled = scrollEnabled;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        MeasureSpecAssertions.assertExplicitMeasureSpec(widthMeasureSpec, heightMeasureSpec);

        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        // Call with the present values in order to re-layout if necessary
        scrollTo(getScrollX(), getScrollY());
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (mRemoveClippedSubviews) {
            updateClippingRect();
        }
    }

//    @Override
//    protected void onAttachedToWindow() {
//        super.onAttachedToWindow();
//        if (mRemoveClippedSubviews) {
//            updateClippingRect();
//        }
//    }

    @Override
    protected void onScrollChanged(int x, int y, int oldX, int oldY) {
        super.onScrollChanged(x, y, oldX, oldY);

        if (mOnScrollDispatchHelper.onScrollChanged(x, y)) {
            if (mRemoveClippedSubviews) {
                updateClippingRect();
            }

            if (mFlinging) {
                mDoneFlinging = false;
            }

            ReactNestedScrollViewHelper.emitScrollEvent(this);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!mScrollEnabled) {
            return false;
        }

        if (super.onInterceptTouchEvent(ev)) {
            NativeGestureUtil.notifyNativeGestureStarted(this, ev);
            ReactNestedScrollViewHelper.emitScrollBeginDragEvent(this);
            mDragging = true;
            return true;
        }

        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!mScrollEnabled) {
            return false;
        }

        int action = ev.getAction() & MotionEvent.ACTION_MASK;
        if (action == MotionEvent.ACTION_UP && mDragging) {
            ReactNestedScrollViewHelper.emitScrollEndDragEvent(this);
            mDragging = false;
        }
        return super.onTouchEvent(ev);
    }

    @Override
    public void setRemoveClippedSubviews(boolean removeClippedSubviews) {
        if (removeClippedSubviews && mClippingRect == null) {
            mClippingRect = new Rect();
        }
        mRemoveClippedSubviews = removeClippedSubviews;
        updateClippingRect();
    }

    @Override
    public boolean getRemoveClippedSubviews() {
        return mRemoveClippedSubviews;
    }

    @Override
    public void updateClippingRect() {
        if (!mRemoveClippedSubviews) {
            return;
        }

        Assertions.assertNotNull(mClippingRect);

        ReactClippingViewGroupHelper.calculateClippingRect(this, mClippingRect);
        View contentView = getChildAt(0);
        if (contentView instanceof ReactClippingViewGroup) {
            ((ReactClippingViewGroup) contentView).updateClippingRect();
        }
    }

    @Override
    public void getClippingRect(Rect outClippingRect) {
        outClippingRect.set(Assertions.assertNotNull(mClippingRect));
    }

    @Override
    public void fling(int velocityY) {
        super.fling(velocityY);
        if (mSendMomentumEvents) {
            mFlinging = true;
            ReactNestedScrollViewHelper.emitScrollMomentumBeginEvent(this);
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    if (mDoneFlinging) {
                        mFlinging = false;
                        ReactNestedScrollViewHelper.emitScrollMomentumEndEvent(ReactNestedScrollView.this);
                    } else {
                        mDoneFlinging = true;
                        ReactNestedScrollView.this.postOnAnimationDelayed(this, ReactNestedScrollViewHelper.MOMENTUM_DELAY);
                    }
                }
            };
            postOnAnimationDelayed(r, ReactNestedScrollViewHelper.MOMENTUM_DELAY);
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (mEndFillColor != Color.TRANSPARENT) {
            final View content = getChildAt(0);
            if (mEndBackground != null && content != null && content.getBottom() < getHeight()) {
                mEndBackground.setBounds(0, content.getBottom(), getWidth(), getHeight());
                mEndBackground.draw(canvas);
            }
        }
        super.draw(canvas);
    }

    public void setEndFillColor(int color) {
        if (color != mEndFillColor) {
            mEndFillColor = color;
            mEndBackground = new ColorDrawable(mEndFillColor);
        }
    }
}
