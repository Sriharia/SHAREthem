package com.tml.sharethem.utils;

import android.support.v7.widget.RecyclerView;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public abstract class RecyclerViewArrayAdapter<T, VH extends RecyclerView.ViewHolder>
        extends RecyclerView.Adapter<VH> {

    protected List<T> mObjects;

    public RecyclerViewArrayAdapter(final List<T> objects) {
        mObjects = objects;
    }

    /**
     * Adds the specified object at the end of the array.
     *
     * @param object The object to add at the end of the array.
     */
    public void add(final T object) {
        mObjects.add(object);
        notifyItemInserted(getItemCount() - 1);
    }

    public List<T> getObjects() {
        return mObjects;
    }

    /**
     * Remove all elements from the list.
     */
    public void clear() {
        final int size = getItemCount();
        mObjects.clear();
        notifyItemRangeRemoved(0, size);
    }

    @Override
    public int getItemCount() {
        return mObjects.size();
    }

    public T getItem(final int position) {
        return mObjects.get(position);
    }

    public long getItemId(final int position) {
        return position;
    }

    /**
     * Returns the position of the specified item in the array.
     *
     * @param item The item to retrieve the position of.
     * @return The position of the specified item.
     */
    public int getPosition(final T item) {
        return mObjects.indexOf(item);
    }

    /**
     * Inserts the specified object at the specified index in the array.
     *
     * @param object The object to insert into the array.
     * @param index  The index at which the object must be inserted.
     */
    public void insert(final T object, int index) {
        mObjects.add(index, object);
        notifyItemInserted(index);

    }

    /**
     * Removes the specified object from the array.
     *
     * @param object The object to remove.
     */
    public void remove(T object) {
        final int position = getPosition(object);
        mObjects.remove(object);
        notifyItemRemoved(position);
    }

    /**
     * Removes the object from specific location.
     *
     * @param location of the object to remove.
     */
    public void remove(int location) {
        mObjects.remove(location);
        notifyItemRemoved(location);
    }


    public void modify(T object, int position) {
        mObjects.set(position, object);
        notifyItemChanged(position);
    }

    public void modify(T object) {
        int pos = mObjects.indexOf(object);
        if (pos == -1) return;
        mObjects.set(pos, object);
        notifyItemChanged(pos);
    }

    /**
     * Sorts the content of this adapter using the specified comparator.
     *
     * @param comparator The comparator used to sort the objects contained in this adapter.
     */
    public void sort(Comparator<? super T> comparator) {
        Collections.sort(mObjects, comparator);
        notifyItemRangeChanged(0, getItemCount());
    }
}