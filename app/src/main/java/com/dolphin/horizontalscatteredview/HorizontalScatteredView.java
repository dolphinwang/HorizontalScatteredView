package com.dolphin.horizontalscatteredview;

import android.content.Context;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.graphics.Rect;
import android.support.v4.util.SparseArrayCompat;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListAdapter;

import com.dolphin.horizontalscatteredview.R;


public class HorizontalScatteredLayout extends AdapterView<ListAdapter> {
    private static final int ROW_COUNT_UNLIMITED = 0;
    private static final int CHILD_COUNT_NO_ROW_LIMIT = -1;

    private ListAdapter mAdapter;

    private SparseArrayCompat<Rect> mItemLocations;
    private SparseArrayCompat<Integer> mRowHeight;

    private int mItemCount;

    private boolean mDataChanged;

    private boolean mInLayout;

    private Rect mItemMargin;

    private ChildRecycler mRecycler;

    private SparseArrayCompat<View> mChildrenArray;

    private int mSelectIndex;

    private int mRowCountLimited = ROW_COUNT_UNLIMITED;
    private int mCountWithRowCountLimited = CHILD_COUNT_NO_ROW_LIMIT;

    private boolean mOverRowLimit;

    private DataSetObserver mDataSetObserver = new DataSetObserver() {

        @Override
        public void onChanged() {
            // 行高数据清空重新计算
            mRowHeight.clear();

            mDataChanged = true;

            final int currentCount = getChildCount();
            final int newCount = mAdapter.getCount();
            if (currentCount > 0 && (currentCount > newCount)) {
                // remove 多余的view
                for (int i = currentCount - 1; i >= newCount; i--) {
                    View removed = mChildrenArray.get(i);
                    mChildrenArray.delete(i);
                    mRecycler.delete(removed);
                }
            }

            mItemCount = mAdapter.getCount();

            if (mSelectIndex >= mItemCount) {
                mSelectIndex = INVALID_POSITION;
                // 把所有选中状态消除
                final int count = mChildrenArray.size();
                for (int i = 0; i < count; i++) {
                    mChildrenArray.get(i).setSelected(false);
                }
            }

            invalidate();
            requestLayout();
            super.onChanged();
        }

        @Override
        public void onInvalidated() {
            super.onInvalidated();
        }

    };

    public HorizontalScatteredLayout(Context context) {
        this(context, null);
    }

    public HorizontalScatteredLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        init();
        applyConfig(context, attrs);
    }

    private void init() {
        mItemMargin = new Rect();
        mItemLocations = new SparseArrayCompat<>();
        mRowHeight = new SparseArrayCompat<>();
        mChildrenArray = new SparseArrayCompat<>();
    }

    private void applyConfig(Context context, AttributeSet attrs) {
        if (attrs == null) {
            return;
        }

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.HorizontalScattered);

        mItemMargin.left = a.getDimensionPixelOffset(
                R.styleable.HorizontalScattered_item_left_margin, 0);
        mItemMargin.top = a.getDimensionPixelOffset(
                R.styleable.HorizontalScattered_item_top_margin, 0);
        mItemMargin.right = a.getDimensionPixelOffset(
                R.styleable.HorizontalScattered_item_right_margin, 0);
        mItemMargin.bottom = a.getDimensionPixelOffset(
                R.styleable.HorizontalScattered_item_bottom_margin, 0);

        mRowCountLimited = a.getInt(
                R.styleable.HorizontalScattered_row_count_limited,
                ROW_COUNT_UNLIMITED);

        a.recycle();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        final int paddingLeft = getPaddingLeft();
        final int paddingRight = getPaddingRight();
        final int paddingTop = getPaddingTop();
        final int paddingBottom = getPaddingBottom();
        // 计算child需要几行
        // 需要注意的是，view默认同一行的child是一样大小的
        // 同一行view的大小由该行的第一个view来决定
        final int childCount = mItemCount;

        if (mDataChanged) {
            mOverRowLimit = false;

            removeAllViewsInLayout();
            if (childCount > 0) {

                int spaceUsed = paddingLeft;
                int lastBottom = paddingTop;
                int rowIndex = 0;
                int columnIndex = 0;

                for (int i = 0; i < childCount; i++) {

                    if (childCount != mAdapter.getCount()) {
                        throw new IllegalStateException(
                                "Data set changed but not call adapter.notifyDataSetChanged()!");
                    }

                    final int viewType = mAdapter.getItemViewType(i);
                    View child = mRecycler.getRecycle(i, viewType);

                    child = mAdapter.getView(i, child, this);

                    final LayoutParams lp = getLayoutParams(child);

                    if (child != null) {

                        int childWidthSpec = lp.width >= 0
                                ? MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY)
                                : MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);

                        int childHeightSpec = lp.height >= 0
                                ? MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY)
                                : MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
                        child.measure(childWidthSpec, childHeightSpec);

                        int childHeight = child.getMeasuredHeight();
                        int childWidth = child.getMeasuredWidth();

                        // 如果这行的高度已经被确定了,且不同
                        if (mRowHeight.get(rowIndex) != null
                                && mRowHeight.get(rowIndex) != 0
                                && childHeight != mRowHeight.get(rowIndex)) {
                            final int rowHeight = mRowHeight.get(rowIndex);
                            childWidth = (int) (((float) rowHeight / childHeight) * childWidth);
                            childHeight = rowHeight;

                            // remeasure
                            child.measure(MeasureSpec.makeMeasureSpec(
                                    childWidth, MeasureSpec.EXACTLY),
                                    MeasureSpec.makeMeasureSpec(childHeight,
                                            MeasureSpec.EXACTLY));
                        }

                        if (childWidth > widthSize - paddingLeft - paddingRight) {
                            childWidth = widthSize - paddingLeft - paddingRight;

                            // remeasure
                            child.measure(MeasureSpec.makeMeasureSpec(
                                    childWidth, MeasureSpec.EXACTLY),
                                    MeasureSpec.makeMeasureSpec(childHeight,
                                            MeasureSpec.EXACTLY));
                        }

                        // 这边的remain是计算了right margin的
                        // 每一行的最后一个item是没有right margin的
                        // 所以这边暂时不把right margin加进去
                        final int spaceRemain = widthSize - spaceUsed
                                - (columnIndex == 0 ? 0 : mItemMargin.left) - paddingRight;

                        boolean ifLastInRow = false;

                        if (spaceRemain < childWidth) { // 如果当前行的空间不够一个cell的宽度，另起一行

                            lastBottom += (mRowHeight.get(rowIndex, 0)
                                    + (rowIndex == 0 ? 0 : mItemMargin.top) + mItemMargin.bottom);
                            spaceUsed = paddingLeft;
                            rowIndex++;
                            columnIndex = 0;

                            // 做一下行限制判断
                            if (mRowCountLimited > ROW_COUNT_UNLIMITED
                                    && mRowCountLimited == rowIndex) {
                                mCountWithRowCountLimited = i;
                                mOverRowLimit = true;

                                if (mRowLimitChangeListener != null) {
                                    mRowLimitChangeListener.overRowLimit();
                                }
                                break;
                            }
                        } else {
                            // 如果剩下的空间比cell宽加上right margin要小，
                            // 说明这行还能放得下这个cell，但放完后没有多余的空间了。
                            if (spaceRemain <= childWidth + mItemMargin.right) {
                                ifLastInRow = true;
                            }
                        }

                        if (columnIndex == 0) {
                            mRowHeight.put(rowIndex, childHeight);
                        }

                        final int left = (spaceUsed + (columnIndex == 0 ? 0
                                : mItemMargin.left));

                        final int top = ((rowIndex == 0 ? 0 : mItemMargin.top) + lastBottom);

                        final int right = left + childWidth;
                        final int bottom = top + childHeight;

                        if (mItemLocations.get(i) == null) {
                            mItemLocations.put(i, new Rect(left, top, right, bottom));
                        } else {
                            Rect rect = mItemLocations.get(i);
                            rect.left = left;
                            rect.top = top;
                            rect.right = right;
                            rect.bottom = bottom;
                        }

                        if (ifLastInRow) { // 如果这个cell是这行的最后一个，那么已使用的宽度可以是比总宽度大的任意值，反正下一次计算的时候，都是放不下任何的东西了。
                            spaceUsed = widthSize;
                        } else {
                            spaceUsed += ((columnIndex == 0 ? 0 : mItemMargin.left) + mItemMargin.right + childWidth);
                        }
                        columnIndex++;

                        addViewInLayout(child, i, lp, true);
                        mChildrenArray.put(i, child);
                        mRecycler.addActive(child, i, viewType);
                    }
                }
            }

            mRecycler.moveToRecycle();
        }

        heightSize = paddingTop + paddingBottom;

        for (int i = 0; i < mRowHeight.size(); i++) {
            heightSize += ((i == 0 ? 0 : mItemMargin.top)
                    + (i == mRowHeight.size() - 1 ? 0 : mItemMargin.bottom) + mRowHeight
                    .get(i));
        }
        heightMeasureSpec = MeasureSpec.makeMeasureSpec(heightSize,
                MeasureSpec.EXACTLY);
        widthMeasureSpec = MeasureSpec.makeMeasureSpec(widthSize,
                MeasureSpec.EXACTLY);

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {

        if (mDataChanged) {

            mInLayout = true;
            final int count = mChildrenArray.size();
            for (int i = 0; i < count; i++) {

                if (mCountWithRowCountLimited >= 0
                        && i >= mCountWithRowCountLimited) {
                    break;
                }

                View child = mChildrenArray.get(i);

                // 不太应该进这个分支，但是为了万一
                if (child == null) {
                    continue;
                }

                final Rect location = mItemLocations.get(i);
                child.layout(location.left, location.top, location.right, location.bottom);
            }
            mInLayout = false;
            mDataChanged = false;
        }
        super.onLayout(changed, left, top, right, bottom);
    }

    /**
     * Gets a child's layout parameters, defaults if not available.
     */
    private ViewGroup.LayoutParams getLayoutParams(View child) {
        ViewGroup.LayoutParams layoutParams = child.getLayoutParams();
        if (layoutParams == null) {
            layoutParams = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        return layoutParams;
    }

    @Override
    public ListAdapter getAdapter() {
        return mAdapter;
    }

    @Override
    public void setAdapter(ListAdapter adapter) {
        removeAllViewsInLayout();
        if (mAdapter != null) {
            mAdapter.unregisterDataSetObserver(mDataSetObserver);
        }

        mAdapter = adapter;
        resetLayout();

        if (mAdapter != null) {
            mAdapter.registerDataSetObserver(mDataSetObserver);
            mItemCount = mAdapter.getCount();
        }

        requestLayout();
    }

    @Override
    public void requestLayout() {
        if (!mInLayout) {
            super.requestLayout();
        }
    }

    private void resetLayout() {
        mItemLocations.clear();
        mRowHeight.clear();
        mRecycler = new ChildRecycler(mAdapter.getViewTypeCount());
        mChildrenArray.clear();
        mItemCount = 0;
        mSelectIndex = INVALID_POSITION;
        mDataChanged = true;
        mInLayout = false;
    }

    @Override
    public View getSelectedView() {
        if (mSelectIndex == INVALID_POSITION) {
            return null;
        }

        return mSelectIndex < mChildrenArray.size() ? mChildrenArray
                .get(mSelectIndex) : null;
    }

    @Override
    public void setSelection(int position) {
        if (position < mChildrenArray.size()) {
            mChildrenArray.get(position).setSelected(true);
            mSelectIndex = position;
        }
    }

    public void setRowCountLimited(int limit) {
        if (limit < 0) {
            mRowCountLimited = ROW_COUNT_UNLIMITED;
            return;
        }

        mRowCountLimited = limit;
        mCountWithRowCountLimited = CHILD_COUNT_NO_ROW_LIMIT;

        mDataChanged = true;
        mRowHeight.clear();
        requestLayout();
    }

    public boolean overRowLimit() {
        return mOverRowLimit;
    }

    private RowLimitChangeListener mRowLimitChangeListener;

    public void setRowLimitChangeListener(RowLimitChangeListener l) {
        mRowLimitChangeListener = l;
    }

    private static class ChildRecycler {

        private SparseArrayCompat<View>[] mRecycle;

        private SparseArrayCompat<View>[] mActive;

        ChildRecycler(int typeCount) {
            if (typeCount > 0) {
                mRecycle = new SparseArrayCompat[typeCount];
                mActive = new SparseArrayCompat[typeCount];
            }
        }

        View getRecycle(int index, int type) {
            if (mRecycle == null) {
                return null;
            }

            if (type >= mRecycle.length) {
                throw new IllegalArgumentException("Value of view type " + type + " is larger than adapter#getViewTypeCount!");
            }

            SparseArrayCompat<View> viewCache = mRecycle[type];
            if (viewCache == null) {
                return null;
            }

            // 先查找对应位置的
            View view = viewCache.get(index);
            if (view == null) {
                // 找不到的话，就取第一个
                index = viewCache.keyAt(0);
                view = viewCache.get(index);
            }
            viewCache.delete(index);

            return view;
        }

        void addActive(View view, int index, int type) {
            if (view == null || type < 0) {
                return;
            }

            SparseArrayCompat<View> viewCache = mActive[type];
            if (viewCache == null) {
                viewCache = new SparseArrayCompat<>();
                mActive[type] = viewCache;
            }

            // check exists
            final int originalIndex = viewCache.indexOfValue(view);
            if (originalIndex != -1) {
                final int originalKey = viewCache.keyAt(originalIndex);
                if (originalKey != index) {
                    throw new IllegalStateException("View object conflict, view "
                            + view.toString() + " has already in cache! Original index is " + originalKey);
                }
            }

            viewCache.put(index, view);
        }

        void moveToRecycle() {
            if (mRecycle == null || mActive == null) {
                return;
            }

            // move all active views to recycle
            for (int i = 0; i < mActive.length; i++) {
                mRecycle[i] = mActive[i];
                mActive[i] = null;
            }
        }

        void delete(View view) {
            if (mRecycle == null) {
                return;
            }

            for (SparseArrayCompat<View> cache : mRecycle) {
                if (cache == null) {
                    continue;
                }

                final int index = cache.indexOfValue(view);
                if (index != -1) {
                    cache.removeAt(index);

                    break;
                }
            }
        }
    }

    public interface RowLimitChangeListener {
        void overRowLimit();
    }
}
