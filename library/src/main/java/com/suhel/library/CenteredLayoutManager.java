/*
 * MIT License
 *
 * Copyright (c) 2019 Suhel Chakraborty
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.suhel.library;

import android.support.annotation.FloatRange;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

/**
 * A custom {@link android.support.v7.widget.RecyclerView.LayoutManager} which
 * applies top and bottom offsets to achieve a reel effect
 */
public class CenteredLayoutManager extends RecyclerView.LayoutManager {

    /**
     * Stores the absolute scroll value
     */
    private int mScrollY;

    /**
     * Stores the height of each child assuming
     * the children are homogeneous
     */
    private int mChildHeight;

    /**
     * Stores both the top and bottom offsets
     * applied before the views are laid out
     */
    private int mOffset;

    /**
     * Stores the value of the maximum scroll
     * that can be reached
     */
    private int mMaxScrollY;

    /**
     * Stores the center of the parent
     */
    private int mCenterY;

    /**
     * Stores the reference to the {@link ChildTransformer}
     */
    private ChildTransformer mChildTransformer;

    /**
     * Used to get the current {@link ChildTransformer}
     *
     * @return The {@link ChildTransformer} currently being used
     */
    public ChildTransformer getChildTransformer() {
        return mChildTransformer;
    }

    /**
     * Used to set the current {@link ChildTransformer}
     *
     * @param childTransformer The {@link ChildTransformer} to be used.
     *                         Can be {@literal null}
     */
    public void setChildTransformer(@Nullable ChildTransformer childTransformer) {
        this.mChildTransformer = childTransformer;
    }

    /**
     * @return The left edge of the viewable area
     */
    private int getParentLeft() {
        return getPaddingLeft();
    }

    /**
     * @return The top edge of the viewable area
     */
    private int getParentTop() {
        return getPaddingTop();
    }

    /**
     * @return The right edge of the viewable area
     */
    private int getParentRight() {
        return getWidth() - getPaddingRight();
    }

    /**
     * @return The bottom edge of the viewable area
     */
    private int getParentBottom() {
        return getHeight() - getPaddingBottom();
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        return new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (getChildCount() > 0) {
            detachAndScrapAttachedViews(recycler);
        }

        if (state.getItemCount() == 0) {
            return;
        }

        calculateDimensions(recycler, state);
        render(recycler, state);
        recycle(recycler);
    }

    @Override
    public boolean canScrollVertically() {
        return true;
    }

    @Override
    public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler, RecyclerView.State state) {
        final int lastScrollY = mScrollY;
        mScrollY = Math.min(Math.max(mScrollY + dy, 0), mMaxScrollY);
        detachAndScrapAttachedViews(recycler);
        render(recycler, state);
        recycle(recycler);
        return mScrollY - lastScrollY;
    }

    /**
     * Returns the position of item highlighted in the middle of the
     * screen
     *
     * @return Item index starting from 0
     */
    public int getSelectedItemPosition() {
        return (int) (((float) mScrollY / mChildHeight) + 0.5f);
    }

    /**
     * Used to layout children after applying top and bottom offset
     *
     * @param recycler The {@link android.support.v7.widget.RecyclerView.Recycler}
     *                 passed for getting inflated children
     * @param state    The {@link android.support.v7.widget.RecyclerView.State}
     *                 passed for getting item count
     */
    private void render(RecyclerView.Recycler recycler, RecyclerView.State state) {
        if (state.getItemCount() < 2) {
            return;
        }
        final int startIndex = (mScrollY >= mOffset) ?
                Math.min((mScrollY - mOffset) / mChildHeight, state.getItemCount() - 1) :
                0;

        int top = (mScrollY >= mOffset) ?
                -((mScrollY - mOffset) % mChildHeight) :
                mOffset - mScrollY;

        int bottom;

        for (int i = startIndex; i < state.getItemCount() && top < getParentBottom(); i++, top = bottom) {
            View v = recycler.getViewForPosition(i);
            addView(v);
            measureChildWithMargins(v, 0, 0);
            bottom = top + getDecoratedMeasuredHeight(v);
            layoutDecoratedWithMargins(v, getParentLeft(), top, getParentRight(), bottom);

            if (mChildTransformer != null) {
                final int childCenterY = (top + bottom) / 2;
                final float childCenterOffset = (float) (childCenterY - mCenterY);
                final float childCenterOffsetRatio = Math.min(Math.max(childCenterOffset / mCenterY, -1.0f), 1.0f);
                mChildTransformer.onApplyTransform(v, i, i - startIndex, childCenterOffsetRatio);
            }
        }
    }

    /**
     * Used to recycle the children which are out of bounds and can be recycled
     *
     * @param recycler The {@link android.support.v7.widget.RecyclerView.Recycler}
     *                 passed to recycle views out of view bounds
     */
    private void recycle(@NonNull RecyclerView.Recycler recycler) {
        final int childCount = getChildCount();
        boolean foundFirst = false;
        int first = 0;
        int last = 0;
        for (int i = 0; i < childCount; i++) {
            final View v = getChildAt(i);
            if (v == null)
                continue;

            if (getDecoratedRight(v) >= getParentLeft() && getDecoratedLeft(v) <= getParentRight() && getDecoratedBottom(v) >= getParentTop() && getDecoratedTop(v) <= getParentBottom()) {
                if (!foundFirst) {
                    first = i;
                    foundFirst = true;
                }
                last = i;
            }
        }
        for (int i = childCount - 1; i > last; i--) {
            removeAndRecycleViewAt(i, recycler);
        }
        for (int i = first - 1; i >= 0; i--) {
            removeAndRecycleViewAt(i, recycler);
        }
    }

    /**
     * Calculates all the required dimensions based on the current {@link android.support.v7.widget.RecyclerView.State}
     *
     * @param recycler The {@link android.support.v7.widget.RecyclerView.Recycler}
     *                 passed to inflate a scrap or dummy view to cache the height
     *                 in mChildHeight
     * @param state    The {@link android.support.v7.widget.RecyclerView.State}
     *                 passed to get the item count
     */
    private void calculateDimensions(RecyclerView.Recycler recycler, RecyclerView.State state) {
        mCenterY = (getParentTop() + getParentBottom()) / 2;
        View scrap = recycler.getViewForPosition(0);
        addView(scrap);
        measureChildWithMargins(scrap, 0, 0);
        mChildHeight = getDecoratedMeasuredHeight(scrap);
        detachAndScrapView(scrap, recycler);
        mOffset = (getHeight() - mChildHeight) / 2;
        mMaxScrollY = (2 * mOffset) + (state.getItemCount() * mChildHeight) - getHeight();
        mScrollY = Math.min(mMaxScrollY, mScrollY);
    }

    /**
     * An interface used as an interceptor before children are laid out
     * to apply transformations on them
     */
    public interface ChildTransformer {

        /**
         * Called before adding each child
         *
         * @param child          The child {@link View} to transform
         * @param index          The position of the child in the adapter
         * @param screenPosition The position of the child on screen
         * @param centerOffset   The value of the offset of the child from the center.
         *                       Negative value indicates child is above parent center
         */
        void onApplyTransform(@NonNull View child,
                              @IntRange(from = 0, to = Integer.MAX_VALUE) int index,
                              @IntRange(from = 0, to = Integer.MAX_VALUE) int screenPosition,
                              @FloatRange(from = -1.0f, to = 1.0f) float centerOffset);

    }

}
