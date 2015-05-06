package com.dolphin.horizontalscatteredview;

import android.content.Context;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListAdapter;


public class HorizontalScatteredView extends AdapterView<ListAdapter> {

    private static final int ROW_COUNT_UNLIMITED = 0;

    private ListAdapter mAdapter;

    /*
    To store the location of cell.
     */
    private SparseArray<Rect> mItemLocations;

    /*
    To store the height of each row.
     */
    private SparseArray<Integer> mRowHeight;

    /*
    Count of cell.
     */
    private int mItemCount;

    /*
    To indicator that whether data has benn changed.
     */
    private boolean mDataChanged;

    private boolean mInLayout;

    /*
    To store margin between cells which user set.
     */
    private Rect mItemMargin;

    /*
    To store cell for re-use.
     */
    private SparseArray<View> mChildrenArray;

    private int mSelectIndex;

    private int mRowCountLimited = ROW_COUNT_UNLIMITED;
    private int mCountWithRowCountLimited = -1;

    private DataSetObserver mDataSetObserver = new DataSetObserver() {

        @Override
        public void onChanged() {
            // Clear stored height of rows, re-calculate later.
            mRowHeight.clear();

            mDataChanged = true;

            final int currentCount = getChildCount();
            final int newCount = mAdapter.getCount();
            if (currentCount > 0 && (currentCount > newCount)) {
                // Remove views which will not be used.
                for (int i = currentCount - 1; i >= newCount; i--) {
                    mChildrenArray.remove(i);
                }
            }

            mItemCount = mAdapter.getCount();

            // If selected cell will disappear, clear index of selection.
            if (mSelectIndex >= mItemCount) {
                mSelectIndex = INVALID_POSITION;
                // 把所有选中状态消除
                final int count = mChildrenArray.size();
                for (int i = 0; i < count; i++) {
                    mChildrenArray.get(i).setSelected(false);
                }
            }

            // Call view re-measure and re-draw.
            invalidate();
            requestLayout();
            super.onChanged();
        }

        @Override
        public void onInvalidated() {
            super.onInvalidated();
        }

    };

    public HorizontalScatteredView(Context context) {
        this(context, null);
    }

    public HorizontalScatteredView(Context context, AttributeSet attrs) {
        super(context, attrs);

        init();
        applyConfig(context, attrs);
    }

    private void init() {
        mItemMargin = new Rect();
        mItemLocations = new SparseArray<Rect>();
        mRowHeight = new SparseArray<Integer>();
        mChildrenArray = new SparseArray<View>();
    }

    private void applyConfig(Context context, AttributeSet attrs) {
        if (attrs == null) {
            return;
        }

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.HorizontalScattered);

        mItemMargin.left = a.getDimensionPixelOffset(0,
                R.styleable.HorizontalScattered_item_left_margin);
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
        // Calculate how many rows need.
        // Notice that, every single cell in one row has the same height,
        // according to the height of the first cell in this row.
        final int childCount = mItemCount;

        if (mDataChanged) {
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

                    View child = mChildrenArray.get(i);

                    child = mAdapter.getView(i, child, this);

                    final LayoutParams lp = getLayoutParams(child);

                    if (child != null) {

                        int childWidth;
                        int childHeight;

                        boolean childSizeChanged = false;

                        child.measure(MeasureSpec.makeMeasureSpec(0,
                                MeasureSpec.UNSPECIFIED), MeasureSpec
                                .makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));

                        childHeight = child.getMeasuredHeight();
                        childWidth = child.getMeasuredWidth();

                        // If height of this row has been fixed, and this cell's height is different of it.
                        // We need re-measure this cell to match.
                        final Integer fixedRowHeight = mRowHeight.get(rowIndex);
                        if (fixedRowHeight != null && fixedRowHeight != 0
                                && childHeight != fixedRowHeight) {
                            childWidth = (int) (((float) fixedRowHeight / childHeight) * childWidth);
                            childHeight = fixedRowHeight;

                            childSizeChanged = true;
                        }

                        // Ensure that cell's width will not be larger than row provide.
                        if (childWidth > widthSize - paddingLeft - paddingRight) {
                            childWidth = widthSize - paddingLeft - paddingRight;

                            childSizeChanged = true;
                        }

                        // If size changed, re-measure it.
                        if (childSizeChanged) {
                            // remeasure
                            child.measure(MeasureSpec.makeMeasureSpec(
                                            childWidth, MeasureSpec.EXACTLY),
                                    MeasureSpec.makeMeasureSpec(childHeight,
                                            MeasureSpec.EXACTLY));
                        }

                        final int spaceRemain = widthSize - spaceUsed
                                - (columnIndex == 0 ? 0 : mItemMargin.left) - paddingRight;

                        boolean ifLastInRow = false;

                        if (spaceRemain < childWidth) { // If space remain of current row less than cell's width, this cell will be putted on next row.

                            // Re-set some parameters.
                            lastBottom += (mRowHeight.get(rowIndex, 0)
                                    + (rowIndex == 0 ? 0 : mItemMargin.top) + mItemMargin.bottom);
                            spaceUsed = paddingLeft;
                            rowIndex++;
                            columnIndex = 0;

                            // If reach the row limit, it's the end.
                            if (mRowCountLimited > ROW_COUNT_UNLIMITED
                                    && mRowCountLimited == rowIndex) {
                                mCountWithRowCountLimited = i;
                                break;
                            }
                        } else {
                            // If space remain less than cell's width within it's right margin,
                            // represent that there's not more space to put another cell after this cell be putted.
                            // So, this cell is the last cell in current row.
                            if (spaceRemain <= childWidth + mItemMargin.right) {
                                ifLastInRow = true;
                            }
                        }

                        // If is the first cell in row, record it's height as row's height.
                        if (columnIndex == 0) {
                            mRowHeight.put(rowIndex, childHeight);
                        }

                        // Calculate left, top, right and bottom of this cell.
                        final int left = (spaceUsed + (columnIndex == 0 ? 0
                                : mItemMargin.left));

                        final int top = ((rowIndex == 0 ? 0 : mItemMargin.top) + lastBottom);

                        final int right = left + childWidth;
                        final int bottom = top + childHeight;

                        // Record the location data
                        if (mItemLocations.get(i) == null) {
                            mItemLocations.put(i, new Rect(left, top, right,
                                    bottom));
                        } else {
                            Rect rect = mItemLocations.get(i);
                            rect.left = left;
                            rect.top = top;
                            rect.right = right;
                            rect.bottom = bottom;
                        }

                        // Record used space of this row, if reach the last cell, spaceUsed can be any of number larger than row's width.
                        // Here, we record it as row's width.
                        if (ifLastInRow) {
                            spaceUsed = widthSize;
                        } else {
                            spaceUsed += ((columnIndex == 0 ? 0 : mItemMargin.left) + mItemMargin.right + childWidth);
                        }
                        columnIndex++;

                        addViewInLayout(child, i, lp, true);
                        mChildrenArray.put(i, child);
                    }
                }
            }
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
    protected void onLayout(boolean changed, int left, int top, int right,
                            int bottom) {

        if (mDataChanged) {

            mInLayout = true;
            final int count = mChildrenArray.size();
            for (int i = 0; i < count; i++) {

                // If reach the row's limit number, game over.
                if (mCountWithRowCountLimited >= 0
                        && i >= mCountWithRowCountLimited) {
                    break;
                }

                View child = mChildrenArray.get(i);

                // For protect.
                if (child == null) {
                    continue;
                }

                final Rect location = mItemLocations.get(i);
                child.layout(location.left, location.top, location.right,
                        location.bottom);
            }
            mInLayout = false;
            mDataChanged = false;
        }
        super.onLayout(changed, left, top, right, bottom);
    }

    /**
     * Gets a child's layout parameters, defaults if not available.
     */
    private LayoutParams getLayoutParams(View child) {
        LayoutParams layoutParams = child.getLayoutParams();
        if (layoutParams == null) {
            layoutParams = new LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT);
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

        mDataChanged = true;
        mRowHeight.clear();
        requestLayout();
    }
}
