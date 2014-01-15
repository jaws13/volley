/**
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.volley.toolbox;

import android.content.Context;
import android.graphics.*;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader.ImageContainer;
import com.android.volley.toolbox.ImageLoader.ImageListener;
import android.util.Log;

/**
 * Handles fetching an image from a URL as well as the life-cycle of the
 * associated request.
 */
public class NetworkImageView extends ImageView {
    public enum BitmapProfile{
        ProfileLandingView,
        ProfileDetailsView,
        ProfileGoodOldView
    }

    /** The URL of the network image to load */
    private String mUrl;

    /**
     * Resource ID of the image to be used as a placeholder until the network image is loaded.
     */
    private int mDefaultImageId;

    /**
     * Resource ID of the image to be used if the network response fails.
     */
    private int mErrorImageId;

    /** Local copy of the ImageLoader. */
    private ImageLoader mImageLoader;

    /** Current ImageContainer. (either in-flight or finished) */
    private ImageContainer mImageContainer;

    private int mMaxWidth;
    private int mMaxHeight;

    private int mBestHeight;
    private int mBestWidth;
    private BitmapProfile mScalingProfile;
    public NetworkImageView(Context context) {
        this(context, null);
    }

    public NetworkImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NetworkImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setImageUrl(String url, ImageLoader imageLoader){
        mUrl = url;
        mImageLoader = imageLoader;
        mScalingProfile = BitmapProfile.ProfileGoodOldView;
        mBestHeight = 0;
        mBestWidth = 0;
        // The URL has potentially changed. See if we need to load it.
        loadImageIfNecessary(false);

    }
    /**
     * Sets URL of the image that should be loaded into this view. Note that calling this will
     * immediately either set the cached image (if available) or the default image specified by
     * {@link NetworkImageView#setDefaultImageResId(int)} on the view.
     *
     * NOTE: If applicable, {@link NetworkImageView#setDefaultImageResId(int)} and
     * {@link NetworkImageView#setErrorImageResId(int)} should be called prior to calling
     * this function.
     *
     * @param url The URL that should be loaded into this ImageView.
     * @param imageLoader ImageLoader that will be used to make the request.
     */
    public void setImageUrl(String url, ImageLoader imageLoader, int bestWidth, int bestHeight, BitmapProfile profile) {
        mUrl = url;
        mImageLoader = imageLoader;
        mScalingProfile = profile;
        mBestHeight = bestHeight;
        mBestWidth = bestWidth;
        // The URL has potentially changed. See if we need to load it.
        loadImageIfNecessary(false);
    }

    public void setMaxDimensions(int maxWidth, int maxHeight){
        mMaxHeight = maxHeight;
        mMaxWidth = mMaxWidth;
    }

    /**
     * Sets the default image resource ID to be used for this view until the attempt to load it
     * completes.
     */
    public void setDefaultImageResId(int defaultImage) {
        mDefaultImageId = defaultImage;
    }

    /**
     * Sets the error image resource ID to be used for this view in the event that the image
     * requested fails to load.
     */
    public void setErrorImageResId(int errorImage) {
        mErrorImageId = errorImage;
    }

    /**
     * Loads the image for the view if it isn't already loaded.
     * @param isInLayoutPass True if this was invoked from a layout pass, false otherwise.
     */
    private void loadImageIfNecessary(final boolean isInLayoutPass) {
        int width = getWidth();
        int height = getHeight();

        boolean isFullyWrapContent = getLayoutParams() != null
                && getLayoutParams().height == LayoutParams.WRAP_CONTENT
                && getLayoutParams().width == LayoutParams.WRAP_CONTENT;
        // if the view's bounds aren't known yet, and this is not a wrap-content/wrap-content
        // view, hold off on loading the image.
        if (width == 0 && height == 0 && !isFullyWrapContent) {
            return;
        }

        // if the URL to be loaded in this view is empty, cancel any old requests and clear the
        // currently loaded image.
        if (TextUtils.isEmpty(mUrl)) {
            if (mImageContainer != null) {
                mImageContainer.cancelRequest();
                mImageContainer = null;
            }
            setImageBitmap(null);
            return;
        }

        // if there was an old request in this view, check if it needs to be canceled.
        if (mImageContainer != null && mImageContainer.getRequestUrl() != null) {
            if (mImageContainer.getRequestUrl().equals(mUrl)) {
                // if the request is from the same URL, return.
                return;
            } else {
                // if there is a pre-existing request, cancel it if it's fetching a different URL.
                mImageContainer.cancelRequest();
                setImageBitmap(null);
            }
        }

        // The pre-existing content of this view didn't match the current URL. Load the new image
        // from the network.
        ImageContainer newContainer = mImageLoader.get(mUrl,
                new ImageListener() {
                    int errorImageResId = 0;
                    int defaultImageResId = 0;
                    boolean shouldAnimate = false;
                    int ANIMATION_DURATION_MS = 100;
            @Override
            public void onErrorResponse(VolleyError error) {
                if (errorImageResId != 0) {
                    NetworkImageView.this.setImageResource(errorImageResId);
                }
            }

            @Override
            public void onResponse(ImageContainer response, boolean isImmediate, boolean fromDiskCache) {
                if (response.getBitmap() != null) {
                    Bitmap displayBitmap = null;
                    String cacheKey = null;
                    if(mScalingProfile.equals(BitmapProfile.ProfileLandingView)){
                        if(!fromDiskCache){
                            if(response.getBitmap().isRecycled()){
                                cacheKey = mImageLoader.getCacheKey(mUrl, mBestWidth, mBestHeight);
                            }else{
                                displayBitmap = getModifiedBitmap(response.getBitmap(), mBestWidth, mBestHeight);
                                cacheKey = mImageLoader.getCacheKey(mUrl, mBestWidth, mBestHeight);
                                mImageLoader.putBitmap(cacheKey, displayBitmap);
                            }
                        }else{
                            displayBitmap = response.getBitmap();
                        }
                    }else if(mScalingProfile.equals(BitmapProfile.ProfileDetailsView)){
                        int width = response.getBitmap().getWidth();
                        int height = response.getBitmap().getHeight();
                        if (height > mBestHeight) {
                            float tempWidth = (width * mBestHeight) / height;
                            width = (int) tempWidth;
                        }

                        if(!fromDiskCache){
                            if (width > mBestWidth) {
                                float tempHeight = (height * mBestWidth) / width;
                                height = (int) tempHeight;

                            }
                            displayBitmap = getModifiedBitmap(response.getBitmap(), width, height);
                            cacheKey = mImageLoader.getCacheKey(mUrl, width, height);
                            mImageLoader.putBitmap(cacheKey, displayBitmap);
                        }else{
                            displayBitmap = response.getBitmap();
                        }
                    }else{
                        //its one of those good old views - nothing really for us to do here
                        int width = response.getBitmap().getWidth();
                        int height = response.getBitmap().getHeight();

                        if(!fromDiskCache){
                            displayBitmap = getModifiedBitmap(response.getBitmap(), width, height);
                            cacheKey = mImageLoader.getCacheKey(mUrl, width, height);
                            mImageLoader.putBitmap(cacheKey, displayBitmap);
                        }else{
                            displayBitmap = response.getBitmap();
                        }
                    }
                    NetworkImageView.this.setImageBitmap(displayBitmap);

                    /*if (shouldAnimate && fromDiskCache) {
                        // Animation
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                            NetworkImageView.this.setAlpha(0f);
                            NetworkImageView.this.setImageBitmap(displayBitmap);
                            NetworkImageView.this.animate().alpha(1f).setDuration(ANIMATION_DURATION_MS);
                        } else {
                            TransitionDrawable td = new TransitionDrawable(new Drawable[] {
                                    new ColorDrawable(android.R.color.transparent),
                                    new BitmapDrawable(NetworkImageView.this.getResources(), displayBitmap)
                            });
                            NetworkImageView.this.setImageDrawable(td);
                            td.startTransition((int) ANIMATION_DURATION_MS);
                        }
                    } else {
                        NetworkImageView.this.setImageBitmap(displayBitmap);
                    }*/
                }
            }
        }, mBestWidth, mBestHeight);

        // update the ImageContainer to be the new bitmap container.
        mImageContainer = newContainer;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        loadImageIfNecessary(true);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mImageContainer != null) {
            // If the view was bound to an image request, cancel it and clear
            // out the image from the view.
            mImageContainer.cancelRequest();
            setImageBitmap(null);
            // also clear out the container so we can reload the image if necessary.
            mImageContainer = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    private Bitmap getModifiedBitmap(Bitmap originalImage, int width, int height) {
        // here width & height are the desired width & height values)

        // first lets create a new bitmap and a canvas to draw into it.
        Bitmap newBitmap = Bitmap.createBitmap((int) width, (int) height,
                Bitmap.Config.RGB_565);
        float originalWidth = originalImage.getWidth(), originalHeight = originalImage
                .getHeight();
        Canvas canvas = new Canvas(newBitmap);
        float scale = width / originalWidth;
        float xTranslation = 0.0f, yTranslation = (height - originalHeight
                * scale) / 2.0f;
        Matrix transformation = new Matrix();
        // now that we have the transformations, set that for our drawing ops
        transformation.postTranslate(xTranslation, yTranslation);
        transformation.preScale(scale, scale);
        // create a paint and draw into new canvas
        Paint paint = new Paint();
        paint.setFilterBitmap(true);
        canvas.drawBitmap(originalImage, transformation, paint);
        //originalImage.recycle();
        return newBitmap;
    }
}
