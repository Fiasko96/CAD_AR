package com.fiachar.cadarcorev1;

import android.net.Uri;
import android.os.Bundle;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.ar.core.Anchor;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Camera;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.assets.RenderableSource;
import com.google.ar.sceneform.collision.Box;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;
import com.google.firebase.FirebaseApp;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.ListResult;
import com.google.firebase.storage.StorageReference;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Locale;


public class MainActivity extends AppCompatActivity {

    private ArFragment fragment;
    private PointerDrawable pointer = new PointerDrawable();
    private boolean isTracking;
    private boolean isHitting;
    private ModelLoader modelLoader;
    //private Node modelNode;
    // AnchorNode anchorNode;
    //private Scene scene;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);


/*        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });*/

        fragment = (ArFragment)
                getSupportFragmentManager().findFragmentById(R.id.sceneform_fragment);

        assert fragment != null;
        fragment.getArSceneView().getScene().addOnUpdateListener(frameTime -> {
            fragment.onUpdate(frameTime);
            onUpdate();
        });

        modelLoader = new ModelLoader(new WeakReference<>(this));

        initializeGallery();
    }

    private void onUpdate() {
        boolean trackingChanged = updateTracking();
        View contentView = findViewById(android.R.id.content);
        if (trackingChanged) {
            if (isTracking) {
                contentView.getOverlay().add(pointer);
            } else {
                contentView.getOverlay().remove(pointer);
            }
            contentView.invalidate();
        }

        if (isTracking) {
            boolean hitTestChanged = updateHitTest();
            if (hitTestChanged) {
                pointer.setEnabled(isHitting);
                contentView.invalidate();
            }
        }
    }

    private boolean updateTracking() {
        Frame frame = fragment.getArSceneView().getArFrame();
        boolean wasTracking = isTracking;
        isTracking = frame != null &&
                frame.getCamera().getTrackingState() == TrackingState.TRACKING;
        return isTracking != wasTracking;
    }

    private boolean updateHitTest() {
        Frame frame = fragment.getArSceneView().getArFrame();
        android.graphics.Point pt = getScreenCenter();
        List<HitResult> hits;
        boolean wasHitting = isHitting;
        isHitting = false;
        if (frame != null) {
            hits = frame.hitTest(pt.x, pt.y);
            for (HitResult hit : hits) {
                Trackable trackable = hit.getTrackable();
                if (trackable instanceof Plane &&
                        ((Plane) trackable).isPoseInPolygon(hit.getHitPose())) {
                    isHitting = true;
                    break;
                }
            }
        }
        return wasHitting != isHitting;
    }

    private android.graphics.Point getScreenCenter() {
        View vw = findViewById(android.R.id.content);
        return new android.graphics.Point(vw.getWidth() / 2, vw.getHeight() / 2);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void initializeGallery() {
        LinearLayout gallery = findViewById(R.id.gallery_layout);

        gallery.getBackground().setAlpha(0);

        LinearLayout.LayoutParams param = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
                0.33f
        );

        ImageView option1 = new ImageView(this);
        option1.setImageResource(R.drawable.option1);
        option1.setContentDescription("option1");
        option1.setOnClickListener(view -> populateModels());
        //((LinearLayout.LayoutParams)option1.getLayoutParams()).weight = 0.3f;
        gallery.addView(option1, param);

        ImageView option2 = new ImageView(this);
        option2.setImageResource(R.drawable.option2);
        option2.setContentDescription("option2");
        option2.setOnClickListener(view -> addObject(Uri.parse("Headphone_Holder3.sfb")));
        //((LinearLayout.LayoutParams)option1.getLayoutParams()).weight = 0.3f;
        gallery.addView(option2, param);

        ImageView option3 = new ImageView(this);
        option3.setImageResource(R.drawable.option3);
        option3.setContentDescription("option3");
        option3.setOnClickListener(view -> addObject(Uri.parse("Headphones Base.sfb")));
        //((LinearLayout.LayoutParams)option1.getLayoutParams()).weight = 0.3f;
        gallery.addView(option3, param);

    }

    private void addObject(Uri model) {
        Frame frame = fragment.getArSceneView().getArFrame();
        android.graphics.Point pt = getScreenCenter();
        List<HitResult> hits;
        if (frame != null) {
            hits = frame.hitTest(pt.x, pt.y);
            for (HitResult hit : hits) {
                Trackable trackable = hit.getTrackable();
                if (trackable instanceof Plane &&
                        ((Plane) trackable).isPoseInPolygon(hit.getHitPose())) {
                    loadModel(hit.createAnchor(), model);
                    break;

                }
            }
        }
    }
    void loadModel(Anchor anchor, Uri uri) {
        ModelRenderable.builder()
                .setSource(this, RenderableSource.builder()
                        .setSource(this, uri, RenderableSource.SourceType.GLB).build())
                .build()
                .handle((renderable, throwable) -> {
                    MainActivity activity = this;
                    if (activity == null) {
                        return null;
                    } else if (throwable != null) {
                        activity.onException(throwable);
                    } else {
                        activity.addNodeToScene(anchor, renderable);
                    }
                    return null;
                });
        return;
    }
    public class ModelLoader {
        private final WeakReference<MainActivity> owner;
        private static final String TAG = "ModelLoader";

        ModelLoader(WeakReference<MainActivity> owner) {
            this.owner = owner;
        }




    }

    public void addNodeToScene(Anchor anchor, ModelRenderable renderable) {
        AnchorNode anchorNode = new AnchorNode(anchor);
        TransformableNode node = new TransformableNode(fragment.getTransformationSystem());
        //node.setLocalRotation(Quaternion.axisAngle(new Vector3(1f, 0, 0), 90f));
        node.setRenderable(renderable);
        node.setParent(anchorNode);
        fragment.getArSceneView().getScene().addChild(anchorNode);
        node.select();
    }

    public void onException(Throwable throwable) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(throwable.getMessage())
                .setTitle("Codelab error!");
        AlertDialog dialog = builder.create();
        dialog.show();
        return;
    }

    private void populateModels(){
        FirebaseStorage storage = FirebaseStorage.getInstance();

        StorageReference storageRef = storage.getReference();

        StorageReference cadFilesRef = storageRef.child("CAD_Files");

        cadFilesRef.listAll().addOnSuccessListener(new OnSuccessListener<ListResult>() {
            @Override
            public void onSuccess(ListResult listResult) {

                for (StorageReference ref:listResult.getItems()) {

                    ref.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                        @Override
                        public void onSuccess(Uri uri) {
                            //placeModel(anchor, uri.toString());
                            addObject(uri);

                        }
                    });

                }
            }
        });

    }
}

    /*@param scene */

    /*public String generateNodeInfo() {



        if (modelNode == null) {
            return null;
        }
        Camera camera = scene.getCamera();
        String msg = null;

        if (modelNode!= null && modelNode.getRenderable() != null) {
            Vector3 scale = modelNode.getLocalScale();
            Vector3 size = ((Box) modelNode.getCollisionShape()).getSize();
            size.x *= scale.x;
            size.y *= scale.y;
            size.z *= scale.z;
            Vector3 dir = Vector3.subtract(modelNode.getForward(), camera.getForward());
            msg = String.format(Locale.getDefault(), "%s\n%s\n%s",
                    String.format(Locale.getDefault(), "scale: (%.02f, %.02f, %.02f)",
                            scale.x,
                            scale.y,
                            scale.z),
                    String.format(Locale.getDefault(), "size: (%.02f, %.02f, %.02f)",
                            size.x,
                            size.y,
                            size.z),
                    String.format(Locale.getDefault(), "dir: (%.02f, %.02f, %.02f)",
                            dir.x,
                            dir.y,
                            dir.z)
            );

        }
        return msg;
    }*/

   // Button button=findViewById(R.id.button);
    //model.setOnTapListener((hitTestResult, motionEvent1) -> button.setOnClickListener(v -> model.setParent(null)));




