package com.example.lostfound;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

public class PhotoViewActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_view);

        String photoUrl = getIntent().getStringExtra("photoUrl");
        String title = getIntent().getStringExtra("title");

        ImageView photoView = findViewById(R.id.photoView);
        TextView photoTitle = findViewById(R.id.photoTitle);
        ImageView btnClose = findViewById(R.id.btnClose);
        ProgressBar loadingProgress = findViewById(R.id.loadingProgress);

        if (title != null && !title.isEmpty()) {
            photoTitle.setText(title);
        }

        btnClose.setOnClickListener(v -> finish());

        // Load photo with Glide
        if (photoUrl != null && !photoUrl.isEmpty()) {
            Glide.with(this)
                    .load(photoUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(new com.bumptech.glide.request.target.CustomTarget<android.graphics.drawable.Drawable>() {
                        @Override
                        public void onResourceReady(android.graphics.drawable.Drawable resource, com.bumptech.glide.request.transition.Transition<? super android.graphics.drawable.Drawable> transition) {
                            loadingProgress.setVisibility(View.GONE);
                            photoView.setImageDrawable(resource);
                        }

                        @Override
                        public void onLoadCleared(android.graphics.drawable.Drawable placeholder) {
                            photoView.setImageDrawable(placeholder);
                        }

                        @Override
                        public void onLoadFailed(android.graphics.drawable.Drawable errorDrawable) {
                            loadingProgress.setVisibility(View.GONE);
                            photoView.setImageResource(android.R.drawable.ic_menu_report_image);
                        }
                    });
        } else {
            loadingProgress.setVisibility(View.GONE);
            photoView.setImageResource(android.R.drawable.ic_menu_report_image);
        }
    }
}
