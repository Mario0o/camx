package com.uncanny.camx;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Insets;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.Face;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.RecommendedStreamConfigurationMap;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaActionSound;
import android.media.MediaRecorder;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.view.animation.CycleInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.os.HandlerCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.slider.Slider;
import com.google.android.material.textview.MaterialTextView;
import com.uncanny.camx.CustomViews.AutoFitPreviewView;
import com.uncanny.camx.CustomViews.CaptureButton;
import com.uncanny.camx.CustomViews.FaceMeteringRect;
import com.uncanny.camx.CustomViews.FocusCircle;
import com.uncanny.camx.CustomViews.GestureBar;
import com.uncanny.camx.CustomViews.Grids;
import com.uncanny.camx.CustomViews.HorizontalPicker;
import com.uncanny.camx.CustomViews.UncannyChronometer;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

@SuppressWarnings({"FieldMayBeFinal",
        "FieldCanBeLocal"})
public class CamxFragment extends Fragment {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_PERMISSIONS = 200;
    private static final List<String> ACCEPTED_FILES_EXTENSIONS = Arrays.asList("JPG", "JPEG", "DNG");
    private static final FilenameFilter FILENAME_FILTER = (dir, name) -> {
        int index = name.lastIndexOf(46);
        return ACCEPTED_FILES_EXTENSIONS.contains(-1 == index ? "" : name.substring(index + 1).toUpperCase()) && new File(dir, name).length() > 0;
    };
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    private final String [] camID= {"0","1","20","21","22","2","6","3"}; //0,1,2,3,4,5,6,7 in realme and stock android
    // 0   1   2    3    4    5   6   7

    private CameraManager camManager = null;
    private CameraDevice camDevice = null;
    private CameraCaptureSession camSession = null;
    private CameraCharacteristics characteristics = null;
    private CaptureRequest.Builder captureRequest = null;
    private CaptureRequest.Builder camDeviceCaptureRequest;
    private MediaRecorder mMediaRecorder;
    private SurfaceTexture stPreview;

    private static String cameraId = "0";
    private String mVideoFileName;
    private boolean resumed = false, surface = false, ready = false;
    public enum CamState {
        CAMERA,VIDEO,VIDEO_PROGRESSED,PORTRAIT,PRO,NIGHT,SLOMO,TIMEWARP
    }

    private Vector<Surface> surfaceList = new Vector<>();
    private Handler mHandler = new Handler();
    private RelativeLayout appbar;
    private AutoFitPreviewView tvPreview;
    private CaptureButton shutter;
    private MaterialTextView ultra_wide_lens, wide_lens, macro_tele_lens;
    private AppCompatImageButton front_switch;
    private LinearLayout auxDock;
    private TextView zoomText;
    private ShapeableImageView thumbPreview;
    private ImageButton aspectRatio, flash, grid, toolbar_inflate_btn,settings;
    private RelativeLayout dock;
    private ImageReader imgReader;
    private HorizontalPicker mModePicker;
    private Slider zSlider;
    private Grids grids;
    private FocusCircle focusCircle;
    private GestureBar gestureBar;
    private LinearLayout btn_grid1,btn_grid2;
    private UncannyChronometer chronometer;

    private int resultCode = 1;
    private long time;
    private Size imageSize;
    private Size mVideoSize;
    private float mZoom = 1.0f;
    public float zoom_level = 1;
    public float finger_spacing = 0;
    private int cachedHeight;
    private Rect zoom = new Rect();
    //    private double ratioCoeff = 1.3333f;

    private Map<Integer, Size> hRes = new HashMap<>();
    private Map<Integer,Size> map43 = new HashMap<>();
    private Map<Integer, Size> map169 = new HashMap<>();

    private int gridClick = 0;

    //    private boolean pinched = false;
//    private boolean capturing = false;
    private CamState state = CamState.CAMERA;
    private boolean isRecording = false;
    private boolean mflash = false;
    private volatile boolean is_sliding = false;
    private boolean firstTouch = false;
    private boolean ASPECT_RATIO_43 = true;
    private static boolean ASPECT_RATIO_169 = false;

    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    private Runnable hideFocusCircle = new Runnable() {
        @Override
        public void run() {
            if(focusCircle.getVisibility()==View.VISIBLE){
                focusCircle.setVisibility(View.GONE);
            }
        }
    };

    private Runnable hideZoomSlider = new Runnable() {
        @Override
        public void run() {
            if(zSlider.getVisibility()==View.VISIBLE && !is_sliding){
                zSlider.setVisibility(View.GONE);
            }
        }
    };

    private Runnable hideZoomText = new Runnable() {
        @Override
        public void run() {
            if(zoomText.getVisibility()==View.VISIBLE){
                zoomText.setVisibility(View.GONE);
            }
        }
    };

    private Runnable hideAuxDock = new Runnable() {
        @Override
        public void run() {
            if(auxDock.getVisibility()==View.INVISIBLE){
                if(characteristics.get(CameraCharacteristics.LENS_FACING)==CameraCharacteristics.LENS_FACING_FRONT) {
                    auxDock.setVisibility(View.VISIBLE);
                }
            }
            else {
                auxDock.setVisibility(View.GONE);
            }
//            auxDock.setVisibility((auxDock.getVisibility()==View.VISIBLE) ? View.GONE : View.VISIBLE);
        }
    };

    public CamxFragment() {
        //required constructor
    }

    public static CamxFragment newInstance() {
        return new CamxFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (ContextCompat.checkSelfPermission(requireContext(),Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ) {
            Toast.makeText(requireActivity(), "Grant Camera Permissions to Continue", Toast.LENGTH_SHORT).show();
            requestRuntimePermission();
        }
        if(ContextCompat.checkSelfPermission(requireContext(),Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED){
            display_latest_image_from_gallery();
        }

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setAestheticLayout();
        check_aux();
        getHighestResolution();

        View view = inflater.inflate(R.layout.fragment_camx, container, false);

        appbar = view.findViewById(R.id.appbar);
        thumbPreview = view.findViewById(R.id.img_gal);
        tvPreview = view.findViewById(R.id.preview);
        shutter = view.findViewById(R.id.capture);
        ultra_wide_lens = view.findViewById(R.id.ultra_wide);
        wide_lens = view.findViewById(R.id.main_wide);
        macro_tele_lens = view.findViewById(R.id.macro_tele);
        front_switch = view.findViewById(R.id.front_back_switch);
        auxDock = view.findViewById(R.id.aux_cam_switch);
        aspectRatio = view.findViewById(R.id.aspectRatio_btn);
        flash = view.findViewById(R.id.flash);
        grid = view.findViewById(R.id.grid_btn);
        toolbar_inflate_btn = view.findViewById(R.id.settings_btn);
        dock = view.findViewById(R.id.relative_layout_button_dock);
        mModePicker = view.findViewById(R.id.mode_picker_view);
        zoomText = view.findViewById(R.id.zoom_text);
        zSlider = view.findViewById(R.id.zoom_slider);
        grids = view.findViewById(R.id.grid);
        focusCircle = view.findViewById(R.id.focus_circle);
        gestureBar = view.findViewById(R.id.gesture_bar);
        btn_grid1 = view.findViewById(R.id.top_bar_0);
        settings = view.findViewById(R.id.settings);

        return view;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        String [] modes = {"Photo","Video","Slo-mo","Time Warp","Portrait","Night","Pro"};
        mModePicker.setValues(modes);
        mModePicker.setOverScrollMode(View.OVER_SCROLL_NEVER);
        mModePicker.setOnItemSelectedListener(new HorizontalPicker.OnItemSelected() {
            @Override
            public void onItemSelected(int index) {
                mModePicker.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP,HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                switch (index){
                    case 0:
                        Log.e(TAG, "onItemSelected: CAMERA MODE");
                        state = CamState.CAMERA;
                        shutter.colorInnerCircle(CamState.CAMERA);
                        break;
                    case 1:
                        Log.e(TAG, "onItemSelected: VIDEO MODE");
                        state = CamState.VIDEO;
                        mMediaRecorder = new MediaRecorder();
                        requestVideoPermissions();
                        createVideoPreview(tvPreview.getHeight(),tvPreview.getWidth());
                        shutter.colorInnerCircle(CamState.VIDEO);
                        break;
                    case 2:
                        state = CamState.SLOMO;
                        Log.e(TAG, "onItemSelected: SLO-MO MODE");
                        break;
                    case 3:
                        state = CamState.TIMEWARP;
                        Log.e(TAG, "onItemSelected: TIME WARP MODE");
                        break;
                    case 4:
                        state = CamState.PORTRAIT;
                        Log.e(TAG, "onItemSelected: PORTRAIT MODE");
                        break;
                    case 5:
                        state = CamState.NIGHT;
                        Log.e(TAG, "onItemSelected: NIGHT MODE");
                        break;
                }

            }
        });

        aspectRatio.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                aspectRatio.setColorFilter(Color.WHITE);
                if (ASPECT_RATIO_43) {
                    ASPECT_RATIO_169 = true;
                    ASPECT_RATIO_43 = false;

                    tvPreview.animate().alpha(0f)
                            .setDuration(1200).setInterpolator(new CycleInterpolator(1));
                    closeCamera();
                    openCamera();

                    aspectRatio.setColorFilter(ContextCompat.getColor(requireContext(),R.color.purple_200)
                            , PorterDuff.Mode.MULTIPLY);
                    dock.setBackgroundColor(Color.TRANSPARENT);
                } else if (ASPECT_RATIO_169) {
                    ASPECT_RATIO_43 = true;
                    ASPECT_RATIO_169 = false;

                    tvPreview.animate().alpha(0f)
                            .setDuration(1200).setInterpolator(new CycleInterpolator(1));
                    closeCamera();
                    tvPreview.measure(imageSize.getHeight(),imageSize.getWidth());
                    openCamera();

                    aspectRatio.setColorFilter(ContextCompat.getColor(requireContext(),R.color.white)
                            , PorterDuff.Mode.MULTIPLY);
                    dock.setBackgroundColor(Color.BLACK);
                }
            }
        });

        grid.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gridClick+=1;

                if(grids.getVisibility()==View.VISIBLE && gridClick>2){
                    grid.setColorFilter(ContextCompat.getColor(requireContext(),R.color.white)
                            , PorterDuff.Mode.MULTIPLY);
                    grids.setVisibility(View.INVISIBLE);
                }
                else{
                    grid.setColorFilter(ContextCompat.getColor(requireContext(),R.color.purple_200)
                            , PorterDuff.Mode.MULTIPLY);
                    grids.setLines((gridClick == 1 ? 3 : 4));
                    grids.setVisibility(View.VISIBLE);
                    grids.postInvalidate();
                    grids.setViewBounds(tvPreview.getHeight(),tvPreview.getWidth());

                }
                if(gridClick==3) gridClick = 0;

            }
        });

        flash.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                if(mflash){
                    mflash = false;
                    flash.setImageResource(R.drawable.ic_flash_off);
                }
                else {
                    mflash = true;
                    flash.setImageResource(R.drawable.ic_flash_on);
                }
                Log.e(TAG, "onClick: flash : "+mflash);
            }
        });

        settings.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                requireActivity().getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.frag_container,SettingsFragment.newInstance(),"SETTINGS FRAGMENT")
                        .addToBackStack(null)
                        .commit();
            }
        });

        tvPreview.setSurfaceTextureListener(surfaceTextureListener);

        shutter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (state) {
                    case CAMERA:
                        captureImage();
                        shutter.animateShutterButtonStateCamera(.7f);
                        MediaActionSound sound = new MediaActionSound();
                        sound.play(MediaActionSound.SHUTTER_CLICK);
                        //TODO : ADD SEMAPHORE
                        new Handler(Looper.getMainLooper()).postDelayed(() -> display_latest_image_from_gallery(), 1800);
                        break;
                    case VIDEO:
                        chronometer = view.findViewById(R.id.chronometer);
                        if(isRecording){
                            isRecording = false;
                            chronometer.stop();
                            chronometer.setVisibility(View.INVISIBLE);
                            mModePicker.setVisibility(View.VISIBLE);
                            mMediaRecorder.stop(); //TODO: handle stop before preview is generated
                            mMediaRecorder.reset();
                            shutter.resetStateVideo();
                            createVideoPreview(tvPreview.getHeight(),tvPreview.getWidth());
//                            Intent mediaStoreUpdateIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                        }else {
                            startRecording();
                            mModePicker.setVisibility(View.INVISIBLE);
                            chronometer.setBase(SystemClock.elapsedRealtime());
                            chronometer.start();
                            chronometer.setVisibility(View.VISIBLE);
                            shutter.animateShutterButtonStateVideo();
                            mMediaRecorder.start();
                            isRecording = true;
                        }
                        break;
                    case SLOMO:
//                        startSloMo();
                        break;
                    default:
                        break;
                }

            }
        });

        thumbPreview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivityForResult(intent, resultCode);
            }
        });

        ultra_wide_lens.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (check_null_camiID(camID[3])) {
                    if (!getCameraId().equals(camID[3])) {

                        closeCamera();
                        setCameraId(camID[3]);
                        getHighestResolution();
                        openCamera();

                        ultra_wide_lens.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.colored_textview));
                        wide_lens.setBackground(null);
                        macro_tele_lens.setBackground(null);

                        ultra_wide_lens.setTextSize(TypedValue.COMPLEX_UNIT_SP,14);
                        wide_lens.setTextSize(TypedValue.COMPLEX_UNIT_SP,11);
                        macro_tele_lens.setTextSize(TypedValue.COMPLEX_UNIT_SP,11);
                    }
                } else if (check_null_camiID(camID[5])) { //green lens on samsung
                    if (!getCameraId().equals(camID[5])) {
                        closeCamera();
                        setCameraId(camID[5]);
                        getHighestResolution();
                        openCamera();

                        ultra_wide_lens.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.colored_textview));
                        wide_lens.setBackground(null);
                        macro_tele_lens.setBackground(null);

                        ultra_wide_lens.setTextSize(TypedValue.COMPLEX_UNIT_SP,14);
                        wide_lens.setTextSize(TypedValue.COMPLEX_UNIT_SP,11);
                        macro_tele_lens.setTextSize(TypedValue.COMPLEX_UNIT_SP,11);
                    }
                } else if (check_null_camiID(camID[2])) { //green lens on samsung
                    if (!getCameraId().equals(camID[2])) {
                        closeCamera();
                        setCameraId(camID[2]);
                        getHighestResolution();
                        openCamera();

                        ultra_wide_lens.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.colored_textview));
                        wide_lens.setBackground(null);
                        macro_tele_lens.setBackground(null);

                        ultra_wide_lens.setTextSize(TypedValue.COMPLEX_UNIT_SP,14);
                        wide_lens.setTextSize(TypedValue.COMPLEX_UNIT_SP,11);
                        macro_tele_lens.setTextSize(TypedValue.COMPLEX_UNIT_SP,11);
                    }
                }
            }
        });

        wide_lens.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!getCameraId().equals(camID[0])) {
//                    zoom_level = 1;
                    closeCamera();
                    setCameraId(camID[0]);
                    getHighestResolution();
                    openCamera();
                    wide_lens.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.colored_textview));
                    ultra_wide_lens.setBackground(null);
                    macro_tele_lens.setBackground(null);

                    ultra_wide_lens.setTextSize(TypedValue.COMPLEX_UNIT_SP,11);
                    wide_lens.setTextSize(TypedValue.COMPLEX_UNIT_SP,14);
                    macro_tele_lens.setTextSize(TypedValue.COMPLEX_UNIT_SP,11);
                }
            }
        });

        macro_tele_lens.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(check_null_camiID(camID[4])){
                    if(!getCameraId().equals(camID[4])) {
                        closeCamera();
                        setCameraId(camID[4]);
                        getHighestResolution();
                        openCamera();

                        macro_tele_lens.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.colored_textview));
                        ultra_wide_lens.setBackground(null);
                        wide_lens.setBackground(null);

                        ultra_wide_lens.setTextSize(TypedValue.COMPLEX_UNIT_SP,11);
                        wide_lens.setTextSize(TypedValue.COMPLEX_UNIT_SP,11);
                        macro_tele_lens.setTextSize(TypedValue.COMPLEX_UNIT_SP,14);
                    }
                }
                else if (check_null_camiID(camID[6])){
                    if(!getCameraId().equals(camID[6])) {
                        closeCamera();
                        setCameraId(camID[6]);
                        getHighestResolution();
                        openCamera();

                        macro_tele_lens.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.colored_textview));
                        ultra_wide_lens.setBackground(null);
                        wide_lens.setBackground(null);

                        ultra_wide_lens.setTextSize(TypedValue.COMPLEX_UNIT_SP,11);
                        wide_lens.setTextSize(TypedValue.COMPLEX_UNIT_SP,11);
                        macro_tele_lens.setTextSize(TypedValue.COMPLEX_UNIT_SP,14);
                    }
                }
            }
        });

        front_switch.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                if (auxDock.getVisibility() == View.VISIBLE) {
                    closeCamera();
                    setCameraId(camID[1]);
                    getHighestResolution();
                    openCamera();
                    auxDock.setVisibility(View.GONE);
                    front_switch.animate().rotation(180f).setDuration(800);
                } else if (auxDock.getVisibility() == View.GONE) {
                    closeCamera();
                    setCameraId(camID[0]);
                    getHighestResolution();
                    wide_lens.setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.colored_textview));
                    ultra_wide_lens.setBackground(null);
                    macro_tele_lens.setBackground(null);
                    openCamera();
                    auxDock.setVisibility(View.VISIBLE);
                    front_switch.animate().rotation(-180f).setDuration(800);
                }
            }
        });

        tvPreview.setOnTouchListener(touchListener);

        toolbar_inflate_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                inflateButtonMenu();
            }
        });

        gestureBar.setOnTouchListener(new View.OnTouchListener(){
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN){
                    inflateButtonMenu();
                }
                return false;
            }
        });

        try {
            mZoom = getMaxZoom();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        zSlider.addOnChangeListener(new Slider.OnChangeListener() {
            @Override
            public void onValueChange(@NonNull Slider slider, float value, boolean fromUser) {
                try {
                    setZoom(mZoom, value * 4.5f);
                } catch (CameraAccessException | IllegalStateException e) {
                        e.printStackTrace();
                    }

            }
        });
        zSlider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {
                is_sliding = true;
                zSlider.removeCallbacks(hideZoomSlider);
                auxDock.removeCallbacks(hideAuxDock);
            }
            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                auxDock.removeCallbacks(hideAuxDock);
                zSlider.removeCallbacks(hideZoomSlider);
                zoomText.removeCallbacks(hideZoomText);
                is_sliding = false;
                auxDock.postDelayed(hideAuxDock,2000);
                zSlider.postDelayed(hideZoomSlider,2000);
                zoomText.postDelayed(hideZoomText,2000);
            }
        });

    }

    private View.OnTouchListener touchListener = new View.OnTouchListener(){
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if(v.isInTouchMode()){
                v.performClick();
            }
            time = System.currentTimeMillis();
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if(firstTouch && (System.currentTimeMillis() - time) <= 50) {
                    /*
                     * DOUBLE TAP TO ZOOM
                     */
                    Log.e("** DOUBLE TAP**"," second tap ");
                    try {
                        doubleTapZoom();
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                    firstTouch = false;
                } else if(zSlider.getVisibility()!=View.VISIBLE){
                    /*
                     * TOUCH TO FOCUS
                     */
                    firstTouch = true;
                    Handler handler = HandlerCompat.createAsync(Looper.getMainLooper());
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            firstTouch = false;
                        }
                    }, 700);
                    touchToFocus(v,event);
                    time = System.currentTimeMillis();
                    Log.e("** SINGLE  TAP **"," First Tap time  "+time);
                    return false;
                }
            }
            /*
             * PINCH TO ZOOM
             */
            if (event.getPointerCount() > 1) {
                pinchToZoom(event);
            }
            return true;
        }
    };

    private void inflateButtonMenu() {
        btn_grid1 = requireView().findViewById(R.id.top_bar_0);
        btn_grid2 = requireView().findViewById(R.id.top_bar_1);

        if (cachedHeight == appbar.getHeight()) {
            tvPreview.setOnTouchListener(null);
            tvPreview.setClickable(false);
            tvPreview.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v) {
                    tvPreview.setOnTouchListener(touchListener);
                    toolbar_inflate_btn.animate().rotation(0f);

                    btn_grid1.animate().translationY(0f)
                            .setInterpolator(new DecelerateInterpolator());
                    btn_grid2.animate().translationY(0f);

                    RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                            appbar.getWidth(), cachedHeight);
                    appbar.setLayoutParams(layoutParams);
                }
            });
            toolbar_inflate_btn.animate().rotation(-90f).setInterpolator(new DecelerateInterpolator());

            btn_grid1.animate().translationY(28f)
                    .setInterpolator(new DecelerateInterpolator());
            btn_grid2.animate().translationY(22f)
                    .setInterpolator(new DecelerateInterpolator()).setStartDelay(100);

            RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                    appbar.getWidth(), cachedHeight * 3);
            appbar.setLayoutParams(layoutParams);
        }
        else {
            tvPreview.setOnTouchListener(touchListener);
            toolbar_inflate_btn.animate().rotation(0f);

            btn_grid1.animate().translationY(0f)
                    .setInterpolator(new DecelerateInterpolator());
            btn_grid2.animate().translationY(0f);

            RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                    appbar.getWidth(), cachedHeight);
            appbar.setLayoutParams(layoutParams);
        }
    }

    private void setDockHeight() {
        int screenWidth = getScreenWidth(requireActivity());
        int screenHeight = getScreenHeight(requireActivity());
        int tvHeight = (int) (screenWidth * 1.334f);
        int height = screenHeight-tvHeight-appbar.getHeight()-mModePicker.getHeight();
        if(dock.getMinimumHeight()<height) {
            RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(screenWidth
                    , height);
            layoutParams.addRule(RelativeLayout.ABOVE, R.id.mode_picker_view);
            dock.setLayoutParams(layoutParams);
        }
        Log.e(TAG, "SETDOCKHEIGHT : pHeight : "+screenHeight+" tvHeight : "+tvHeight
                +" apppbar : "+appbar.getHeight()+" hor : "+mModePicker.getHeight()
                +" calculatedH : "+height+"minHeight : "+dock.getMinimumHeight());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.e(TAG, "onRequestPermissionsResult: "+ Arrays.toString(permissions));
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                display_latest_image_from_gallery();
            } else {
                Toast.makeText(requireActivity(), "Allow permission to continue", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean flashSupported() {
        return requireActivity().getApplicationContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
    }

    TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
            surface = true;
            stPreview = tvPreview.getSurfaceTexture();
            cachedHeight = appbar.getHeight();
            setDockHeight();
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {
            if(grids.getVisibility()==View.VISIBLE && gridClick == 1){
                grids.postInvalidate();
                grids.setViewBounds(i1,i);
            }
            else if(grids.getVisibility()==View.VISIBLE && gridClick == 2){
                grids.postInvalidate();
                grids.setViewBounds(i1,i);
            }
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            surface = false;
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };

    /**
     *  Z O O O M AND F[ O ]CUS STUFF
     */

    private void doubleTapZoom() throws CameraAccessException {
        auxDock.removeCallbacks(hideAuxDock);
        zSlider.removeCallbacks(hideZoomSlider);
        float maxZoom = 1;
        try {
            maxZoom = getMaxZoom();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        float DT_value = maxZoom/2;

        Log.e(TAG, "doubleTaptoZoom: Zoom Level "+zoom_level);
        if(zoom_level<DT_value){
            setZoom(maxZoom,DT_value);
            zoom_level = DT_value;
            zSlider.setValue(5.0f);
        }
        else if(zoom_level>DT_value){
            setZoom(maxZoom,DT_value);
            zoom_level = DT_value;
            zSlider.setValue(5.0f);
        }
        else if(zoom_level==DT_value){
            setZoom(maxZoom,1f);
            zoom_level = 1f;
            zSlider.setValue(0f);
        }
        auxDock.setVisibility(View.GONE);
        zSlider.setVisibility(View.VISIBLE);

        if(!is_sliding) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                //TODO : ADD SEMAPHORE
                auxDock.setVisibility(View.VISIBLE);
                zSlider.setVisibility(View.INVISIBLE);
            }, 1500);
        }
        Log.e(TAG, "doubleTaptoZoom: D O U B L E - T A P P E D");
    }

    private void touchToFocus(View v,MotionEvent event) {

        //TODO : create separate class & method for this
        focusCircle.removeCallbacks(hideFocusCircle);
        float h = event.getX();
        float w = event.getY();

//        focusCircle.animateInnerCircle();
        focusCircle.setVisibility(View.VISIBLE);
        focusCircle.setPosition((int)h,(int)w,getScreenWidth(requireActivity()));
//        focusCircle.setPivotX((int)h);
//        focusCircle.setPivotY((int)w);
//        focusCircle.animate().scaleX(1.2f).scaleY(1.2f).setDuration(2000).setInterpolator(new CycleInterpolator(1));

        focusCircle.postDelayed(hideFocusCircle,2000);


        Log.e(TAG, "touchToFocus: F O C U S I N G");
        CameraManager manager = (CameraManager)requireActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            characteristics = manager.getCameraCharacteristics(getCameraId());
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        final Rect sensorArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);

        /*
         * here I just flip x,y, but this needs to correspond with the sensor orientation (via SENSOR_ORIENTATION)
         */
        final int y = (int)((event.getX() / (float)v.getWidth())  * (float)sensorArraySize.height());
        final int x = (int)((event.getY() / (float)v.getHeight()) * (float)sensorArraySize.width());

        /*
         * this doesn't represent actual touch size in pixel. Values range in [3, 10]...
         */
        final int halfTouchWidth  = 150; //(int)motionEvent.getTouchMajor();
        final int halfTouchHeight = 150; //(int)motionEvent.getTouchMinor();
        MeteringRectangle focusAreaTouch = new MeteringRectangle(Math.max(x - halfTouchWidth,  0),
                Math.max(y - halfTouchHeight, 0),
                halfTouchWidth  * 2,
                halfTouchHeight * 2,
                MeteringRectangle.METERING_WEIGHT_MAX - 1);

        CameraCaptureSession.CaptureCallback captureCallbackHandler = new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                super.onCaptureCompleted(session, request, result);
//                mManualFocusEngaged = false;

                if (request.getTag() == "FOCUS_TAG") {
                    //the focus trigger is complete -
                    //resume repeating (preview surface will get frames), clear AF trigger
                    camDeviceCaptureRequest.set(CaptureRequest.CONTROL_AF_TRIGGER, null);
                    try {
                        camSession.setRepeatingRequest(camDeviceCaptureRequest.build(), null, null);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
            @Override
            public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
                super.onCaptureFailed(session, request, failure);
                Log.e(TAG, "Manual AF failure: " + failure);
//                mManualFocusEngaged = false;
            }
        };

        //first stop the existing repeating request
//        try {
//            cameraCaptureSessions.stopRepeating();
//        } catch (CameraAccessException e) {
//            e.printStackTrace();
//        }

        //cancel any existing AF trigger (repeated touches, etc.)
        camDeviceCaptureRequest.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
        camDeviceCaptureRequest.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF);
        try {
            camSession.capture(camDeviceCaptureRequest.build(), captureCallbackHandler, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        //Now add a new AF trigger with focus region
        if (isMeteringAreaAFSupported()) {
            camDeviceCaptureRequest.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{focusAreaTouch});
        }

        //TODO:FIX METERING AE
//        if(isMeteringAreaAESupported()){
//            camDeviceCaptureRequest.set(CaptureRequest.CONTROL_AE_REGIONS, new MeteringRectangle[]{focusAreaTouch});
//        }
        camDeviceCaptureRequest.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        camDeviceCaptureRequest.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
        camDeviceCaptureRequest.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
        camDeviceCaptureRequest.setTag("FOCUS_TAG"); //we'll capture this later for resuming the preview

        //then we ask for a single request (not repeating!)
        try {
            camSession.capture(camDeviceCaptureRequest.build(), captureCallbackHandler, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
//        mManualFocusEngaged = true;
    }

    private void pinchToZoom(MotionEvent event){
        auxDock.removeCallbacks(hideAuxDock);
        zSlider.removeCallbacks(hideZoomSlider);
        try {
            float maxzoom = getMaxZoom();
            float current_finger_spacing;
            // Multi touch logic
            current_finger_spacing = getFingerSpacing(event);
            if(finger_spacing != 0){
//                pinched = true;
                if(current_finger_spacing > finger_spacing && maxzoom > zoom_level){
                    zoom_level+=0.5f;
//                    Log.e(TAG, "pinchtoZoom: Zoom In "+zoom_level);

                    auxDock.setVisibility(View.GONE);
                    zSlider.setVisibility(View.VISIBLE);
                    zSlider.setValue((float) getZoomValueSingleDecimal((zoom_level/4.5f)));
                } else if (current_finger_spacing < finger_spacing && zoom_level > 1){
                    zoom_level-=0.5f;
//                    Log.e(TAG, "pinchtoZoom: Zoom Out "+zoom_level);

                    auxDock.setVisibility(View.GONE);
                    zSlider.setVisibility(View.VISIBLE);
                    zSlider.setValue((float) getZoomValueSingleDecimal((zoom_level/4.5f)));
                }
                setZoom(maxzoom,zoom_level);

                if(!is_sliding) {
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        //TODO : ADD SEMAPHORE
                        auxDock.setVisibility(View.VISIBLE);
                        zSlider.setVisibility(View.INVISIBLE);
                    }, 1500);
                }

            }
            finger_spacing = current_finger_spacing;
        } catch (CameraAccessException e) {
            throw new RuntimeException("can not access camera.", e);
        }

    }

    private void setZoom(float maxzoom, float zoom_level) throws CameraAccessException {
        CameraManager manager = (CameraManager) requireActivity().getSystemService(Context.CAMERA_SERVICE);
        CameraCharacteristics characteristics = manager.getCameraCharacteristics(getCameraId());
        Rect m = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);

        zoomText.removeCallbacks(hideZoomText);

        int minW = (int) (m.width() / maxzoom);
        int minH = (int) (m.height() / maxzoom);
        int difW = m.width() - minW;
        int difH = m.height() - minH;
        int cropW = difW /100 *(int)zoom_level;
        int cropH = difH /100 *(int)zoom_level;
        cropW -= cropW & 3;
        cropH -= cropH & 3;
        zoom = new Rect(cropW, cropH, m.width() - cropW, m.height() - cropH);

        String zText = getZoomValueSingleDecimal(zoom_level/4.5f)+"x";
        zoomText.setVisibility(View.VISIBLE);
        zoomText.setText(zText);

        if(!is_sliding){
            //TODO : ADD SEMAPHORE
            zoomText.postDelayed(()-> zoomText.setVisibility(View.INVISIBLE),1800);
        }

        camDeviceCaptureRequest.set(CaptureRequest.SCALER_CROP_REGION, zoom);
        try {
            camSession
                    .setRepeatingRequest(camDeviceCaptureRequest.build(), null, null);
        } catch (CameraAccessException | NullPointerException e) {
            e.printStackTrace();
        }
    }

    private double getZoomValueSingleDecimal(float zoom_level) {
        BigDecimal bd = new BigDecimal(Double.toString(zoom_level));
        bd = bd.setScale(1, RoundingMode.HALF_DOWN);
        return bd.doubleValue();
    }

    private float getFingerSpacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    public float getMaxZoom() throws CameraAccessException {
        CameraManager manager = (CameraManager) requireActivity().getSystemService(Context.CAMERA_SERVICE);
        CameraCharacteristics characteristics = manager.getCameraCharacteristics(getCameraId());
        return (characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM))*4.5f;
    }

    /**
     * CAMERA M E T H O D S
     */

    private void openCamera() {
        if (!resumed || !surface)
            return;

        ;
        hRes = (ASPECT_RATIO_43 ? map43 : map169);
        for (Size item : hRes.values()) {
            imageSize = item;
            Log.e(TAG, "openCamera: imageDimension : " + item);
        }
        tvPreview.measure(imageSize.getHeight(),imageSize.getWidth());
        stPreview.setDefaultBufferSize(imageSize.getWidth(),imageSize.getHeight());

        float ratio = (float) imageSize.getWidth()/(float)imageSize.getHeight();
        Log.e(TAG, "openCamera: ratio : "+ratio);

        surfaceList.clear();
        surfaceList.add(new Surface(stPreview));

        imgReader = ImageReader.newInstance(imageSize.getWidth(),imageSize.getHeight(), ImageFormat.JPEG, 10);
        imgReader.setOnImageAvailableListener(snapshotImageCallback, mBackgroundHandler);
        surfaceList.add(imgReader.getSurface());
        Log.e(TAG, "openCamera: snapshot into ImageReader at " + imageSize.getWidth() + "x" + imageSize.getHeight());

        try {
            if (ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestRuntimePermission();
                return;
            }
            camManager.openCamera(getCameraId(), imageCaptureCallback , mHandler);
        } catch(Exception e) {
            Log.e(TAG, "openCamera: open failed: " + e.getMessage());
        }

    }

    public void captureImage() {
        if(!ready)
            return;
        try {
            //check for ZSL support then only add ZSL request
            captureRequest = camDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequest.addTarget(surfaceList.get(0));
            captureRequest.addTarget(surfaceList.get(1));
            if(mflash) {
                captureRequest.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE);
            }
            else {
                captureRequest.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
            }

            if(zoom_level!=1) {
                captureRequest.set(CaptureRequest.SCALER_CROP_REGION, zoom);
            }
            camSession.capture(captureRequest.build(), snapshotCallback, mHandler); //TODO : ADD HANDLER
        } catch(Exception e) {
            Log.e(TAG, "captureImage: "+e.toString());
        }
    }

    private void closeCamera() {
        if (null != camDevice) {
            camDevice.close();
            camDevice = null;
        }
        if (null != imgReader) {
            imgReader.close();
            imgReader = null;
        }
    }

    private boolean isMeteringAreaAESupported() {
        Integer aeState = characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE);
        return aeState!=null && aeState >=1;
    }

    private boolean isMeteringAreaAFSupported() {
        return characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) >= 1;
    }

    /**
     * VIDEO M E T H O D S
     */

    private void createVideoPreview(int height,int width){
        if (!resumed || !surface)
            return;
        Set<String> set=new HashSet();
        set.add("1");
        set.add("21");

        StreamConfigurationMap map = getCameraCharacteristics().get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        // Size[] mVideoSize = map.getHighResolutionOutputSizes(ImageFormat.JPEG); // HIGHRES MODE
//        Log.e(TAG, "createVideoPreview: "+ Arrays.toString(mVideoSize));
        mVideoSize = getVideoPreviewResolution(map.getOutputSizes(MediaRecorder.class),height,width);
        stPreview.setDefaultBufferSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        tvPreview.setAspectRatio(mVideoSize.getHeight(),mVideoSize.getWidth());
        Surface previewSurface = new Surface(stPreview);
        try {
            camDeviceCaptureRequest = camDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            camDeviceCaptureRequest.addTarget(previewSurface);
            camDevice.createCaptureSession(Arrays.asList(previewSurface,imgReader.getSurface())
                    , new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    camSession = session;
                    try {
                        camSession.setRepeatingRequest(camDeviceCaptureRequest.build(), null,mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                }
            },null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startRecording(){
        try {
            if(!isRecording){
                setupMediaRecorder();
            }
            SurfaceTexture surfaceTexture = tvPreview.getSurfaceTexture();
            surfaceTexture.setDefaultBufferSize(mVideoSize.getWidth(), mVideoSize.getHeight());
            Surface previewSurface = new Surface(surfaceTexture); //TODO : free surface with #release
            Surface recordSurface = mMediaRecorder.getSurface();
            camDeviceCaptureRequest = camDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            camDeviceCaptureRequest.addTarget(previewSurface);
            camDeviceCaptureRequest.addTarget(recordSurface);
            camDevice.createCaptureSession(Arrays.asList(previewSurface, recordSurface, imgReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            camSession = session;
                            try {
                                camSession.setRepeatingRequest(
                                        camDeviceCaptureRequest.build(),null, null
                                );
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                            Log.d(TAG, "onConfigureFailed: startRecord");
                        }
                    }, null);

        }
        catch (IOException|CameraAccessException e){
            e.printStackTrace();
        }

    }

    private Size getVideoPreviewResolution(Size[] outputSizes, int height, int width) {
        ArrayList<Size> sizeArrayList = new ArrayList<>();
        for(Size size : outputSizes){
            float ar = (float) size.getWidth()/ size.getHeight();
            if(size.getHeight() == width && ar > 1.5f){
                sizeArrayList.add(size);
            }
        }
        if(sizeArrayList.size() > 0){
            return Collections.min(sizeArrayList,new CompareSizeByArea());
        }
        else return outputSizes[0];
    }

    private void setupMediaRecorder() throws IOException {
        CamcorderProfile camcorderProfile = CamcorderProfile.get(1,CamcorderProfile.QUALITY_1080P);
        Log.e(TAG, "setupMediaRecorder: CP : h : "+camcorderProfile.videoFrameHeight
                                                +" w : "+camcorderProfile.videoFrameWidth);
        Log.e(TAG, "setupMediaRecorder: vBR : "+camcorderProfile.videoBitRate );
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setAudioSamplingRate(8000);
        mMediaRecorder.setAudioEncodingBitRate(96000);
        mMediaRecorder.setAudioEncodingBitRate(camcorderProfile.audioBitRate);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoEncodingBitRate(16400000);
        mMediaRecorder.setVideoSize(camcorderProfile.videoFrameWidth, camcorderProfile.videoFrameHeight);
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
//        mMediaRecorder.setOrientationHint(90);      //TODO : CHANGE ACCORDING TO SENSOR ORIENTATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mMediaRecorder.setOutputFile(new File("//storage//emulated//0//DCIM//Camera//CamX"
                    +System.currentTimeMillis()+"_"+getCameraId()+".mp4"));
        }
        else {
            mMediaRecorder.setOutputFile("//storage//emulated//0//DCIM//Camera//CamX"
                    +System.currentTimeMillis()+"_"+getCameraId()+".mp4");
        }
//        mMediaRecorder.setOrientationHint(mTotalRotation);
        mMediaRecorder.prepare();
    }

    /**
     *  U N C A N N Y  M E T H O D S
     */

    private void getHighestResolution() {
        Size [] resolutions;
        int highest = 0,iF = 0;
        Size hSize43 = null, hSize169 = null;
        map43.clear();map169.clear();
        Map<Integer,Size> imageFormat_resolution_map = new HashMap<>();
        Map<Integer,Size> imageFormat_resolution_map_169 = new HashMap<>();
        ArrayList<Integer> image_formats = new ArrayList<>();
        image_formats.add(ImageFormat.JPEG);
        image_formats.add(ImageFormat.RAW_PRIVATE);
        image_formats.add(ImageFormat.RAW_SENSOR);
        image_formats.add(ImageFormat.YUV_420_888);
//        image_formats.add(ImageFormat.RAW10);

        characteristics = getCameraCharacteristics();

        if(characteristics!=null){
            for(Integer i:image_formats){
                int previous=0,previous169 = 0;
                resolutions = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(i);
                int imageFormat = 0;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    imageFormat = Integer.parseUnsignedInt(Integer.toHexString(i),16);
                }
                if (resolutions!=null){
                    for (Size resolution : resolutions) {
                        float resolution_coeff = (float)resolution.getWidth() / resolution.getHeight();
//                        Log.e(TAG, "getHighestResolution: imageFormat : 0x"+String.format("%x",imageFormat)+"  resolutions :  "
//                                + resolution.getWidth()+"x"+resolution.getHeight()+" coeff : "+resolution_coeff);
//                        Log.e(TAG, "getHighestResolution: "+resolution.getWidth()+"x"+resolution.getHeight()+" coeff : "+resolution_coeff);
                        if( resolution_coeff> 1.6f && resolution_coeff< 1.9f) {
                            if (previous169<resolution.getHeight()*resolution.getWidth()) {
                                imageFormat_resolution_map_169.put(i, resolution);
                                previous169 = resolution.getHeight()*resolution.getWidth();
                            }
                        }
                        if(resolution_coeff> 1.2f && resolution_coeff< 1.4f) {
                            if (previous < (resolution.getHeight() * resolution.getWidth())) {
                                imageFormat_resolution_map.put(i, resolution);
//                            Log.e(TAG, "getHighestResolution: resolution coeff float : "+((float) resolution.getWidth()/resolution.getHeight())%.2f);
                                previous = resolution.getHeight() * resolution.getWidth();
                            }
                        }
                    }
                }
            }
            Log.e(TAG, "getHighestResolution: imageformatresolutionmap "+imageFormat_resolution_map );
            Log.e(TAG, "getHighestResolution: imageformatresolutionmap 16:9 "+imageFormat_resolution_map_169 );
            Set<Map.Entry<Integer, Size>> set = imageFormat_resolution_map.entrySet();
            Set<Map.Entry<Integer, Size>> set169 = imageFormat_resolution_map_169.entrySet();
            for (Map.Entry<Integer, Size> entry : set) {
                Log.e(TAG, "getHighestResolution: resolution :  "+entry.getValue()+"  Imageformat : "+entry.getKey());
                int c = entry.getValue().getHeight()*entry.getValue().getWidth();
                if(highest<=c){
                    highest=c;
                    iF=entry.getKey();
                    hSize43=entry.getValue();
                }
            }

            map43.put(iF,hSize43);
            highest = 0;iF=0;
            //highest 16 : 9 resolution
            for (Map.Entry<Integer, Size> entry : set169) {
                Log.e(TAG, "getHighestResolution: resolution 16:9 :  "+entry.getValue()+"  Imageformat : "+entry.getKey());
                int c = entry.getValue().getHeight()*entry.getValue().getWidth();
                if(highest<=c){
                    highest=c;
                    iF=entry.getKey();
                    hSize169=entry.getValue();
                }
            }
            map169.put(iF,hSize169);
        }
        Log.e(TAG, "getHighestResolution: mReturn(highest res) : "+ map43.entrySet() );
        Log.e(TAG, "getHighestResolution: map169(highest res 16:9) : "+map169.entrySet() );

        hRes = (ASPECT_RATIO_43 ? map43 : map169);
    }

    private CameraCharacteristics getCameraCharacteristics() {
        camManager = (CameraManager) requireActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            characteristics = camManager.getCameraCharacteristics(getCameraId());
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return characteristics;
    }

    public String getCameraId() {
        return cameraId;
    }

    public void setCameraId(String cameraId) {
        CamxFragment.cameraId = cameraId;
    }

    private void setAestheticLayout() {
        requireActivity().getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                , WindowManager.LayoutParams.FLAG_FULLSCREEN);
        requireActivity().getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    private static int getScreenWidth(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics windowMetrics = activity.getWindowManager().getCurrentWindowMetrics();
            Insets insets = windowMetrics.getWindowInsets()
                    .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars());
            return windowMetrics.getBounds().width() - insets.left - insets.right;
        } else {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            activity.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            return displayMetrics.widthPixels;
        }
    }

    private static int getScreenHeight(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics windowMetrics = activity.getWindowManager().getCurrentWindowMetrics();
            Insets insets = windowMetrics.getWindowInsets()
                    .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars());
            return windowMetrics.getBounds().height() - insets.top - insets.bottom;
        } else {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            activity.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            return displayMetrics.heightPixels;
        }
    }

    private void check_aux() {
        StringBuilder msg = new StringBuilder("CAMID : ");
        new Handler(Looper.getMainLooper()).post(() -> {
            for(int i = 0; i<=33 ; i++){
                try {
                    CameraManager manager = (CameraManager) requireActivity().getSystemService(Context.CAMERA_SERVICE);
                    CameraCharacteristics characteristics = manager.getCameraCharacteristics(String.valueOf(i));
                    if (!characteristics.getAvailableCaptureRequestKeys().isEmpty() ) {
//                            Log.e(TAG, "check_aux: getcamera Characteristics => "+ characteristics.getAvailableCaptureRequestKeys());
                        msg.append(i).append(",");
                        Log.e(TAG, "check_aux: value of array at " + i + " : " + i);
                        Set<String> ids;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            ids = characteristics.getPhysicalCameraIds();
                            for (String id: ids) {
                                Log.e(TAG, "check_aux: getPhysicalCameraIds "+id);
                                msg.append(" pid_").append(id).append("_at_").append(i).append(",");
                            }
                        }
                    }
                }
                catch (IllegalArgumentException | CameraAccessException ignored){ }
            }
//                logtext.setText(msg);
        });
        Toast.makeText(requireActivity(), "COMPLETE EXECUTION cam_aux()", Toast.LENGTH_SHORT).show();
    }

    private boolean check_null_camiID(String id){
        try {
            CameraManager manager = (CameraManager) requireActivity().getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
            return !characteristics.getAvailableCaptureRequestKeys().isEmpty();
        }
        catch (IllegalArgumentException | CameraAccessException ignored){ }
        return false;
    }

    private void display_latest_image_from_gallery() {
        String dirPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + "DCIM/Camera" + "/";
        if(Build.VERSION.SDK_INT<Build.VERSION_CODES.Q){
            File f = new File(dirPath);
            File[] dcimFiles = f.listFiles(FILENAME_FILTER);
            List<File> filesList = new ArrayList<>(Arrays.asList(dcimFiles != null ? dcimFiles : new File[0]));
            if (!filesList.isEmpty()) {
                filesList.sort((file1, file2) -> Long.compare(file2.lastModified(), file1.lastModified()));
                File lastImage = filesList.get(0);
                Uri liu = Uri.fromFile(lastImage);
//                Bitmap bmp = BitmapFactory.decodeFile(String.valueOf(lastImage));
                Bitmap ThumbImage = ThumbnailUtils.extractThumbnail(BitmapFactory.decodeFile(String.valueOf(liu))
                        ,100,100);
                thumbPreview.setImageBitmap(ThumbImage);
            } else {
                Log.e(TAG, "display_latest_image_from_gallery(): Could not find any Image Files [1]");
            }
        }
        else {
            new Handler(Looper.getMainLooper()).post(() -> {
                File f = new File("//storage//emulated//0//DCIM//Camera//");
                File[] dcimFiles = f.listFiles(FILENAME_FILTER);
                List<File> filesList = new ArrayList<>(Arrays.asList(dcimFiles != null ? dcimFiles : new File[0]));
//                Log.e(TAG, "display_latest_image_from_gallery: "+filesList);
                if (!filesList.isEmpty()) {
                    filesList.sort((file1, file2) -> Long.compare(file2.lastModified(), file1.lastModified()));
                    File lastImage = filesList.get(0);
                    Uri liu = Uri.fromFile(lastImage);
                    Bitmap bmp = BitmapFactory.decodeFile(String.valueOf(lastImage));
                    thumbPreview.setImageBitmap(bmp);
                } else {
                    Log.e(TAG, "getAllImageFiles(): Could not find any Image Files");
                }

            });
        }
    }

    @NonNull
    public static ArrayList<Uri> listOfAllImages(Context context) {
        Uri uri;
        Cursor cursor;
        int column_index_data;
        ArrayList<Uri> listOfAllImages = new ArrayList<>();
        String absolutePathOfImage;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            uri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        } else {
            uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        }

        String[] projection  = new String[]{MediaStore.Images.Thumbnails.DATA};

        String orderBy = MediaStore.Video.Media.DATE_MODIFIED;
        cursor = context.getContentResolver().query(uri, projection, null, null, orderBy + " DESC");

        column_index_data = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);

        int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);

        while(cursor.moveToNext()) {
//            absolutePathOfImage = cursor.getString(column_index_data);
            long id = cursor.getLong(idColumn);
            Uri contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,id);
//            listOfAllImages.add(new File(absolutePathOfImage));
            listOfAllImages.add(contentUri);
        }
        cursor.close();
        return listOfAllImages;
    }

    private void requestVideoPermissions() {
        if (ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(requireActivity()
                , Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity()
                    , new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE
                                                             , Manifest.permission.RECORD_AUDIO}
                    , REQUEST_PERMISSIONS);
        }
    }

    private void requestRuntimePermission() {
        if (ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(requireActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED ) {
            ActivityCompat.requestPermissions(requireActivity()
                    , new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}
                    , REQUEST_PERMISSIONS);
        }
    }

    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    protected void stopBackgroundThread() {
        if(mBackgroundThread!=null){
            mBackgroundThread.quitSafely();
        }
        else{
            requireActivity().finishAffinity();
            return;
        }
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public Size getMaxSize(Size... sizes) {
        CameraCharacteristics characteristics = getCameraCharacteristics();
        RecommendedStreamConfigurationMap previewConfig =
                characteristics.getRecommendedStreamConfigurationMap(
                        RecommendedStreamConfigurationMap.USECASE_PREVIEW);
        RecommendedStreamConfigurationMap snapshotConfig =
                characteristics.getRecommendedStreamConfigurationMap(
                        RecommendedStreamConfigurationMap.USECASE_SNAPSHOT);

        if ((previewConfig != null) && (snapshotConfig != null)) {
            Set snapshotSizeSet = snapshotConfig.getOutputSizes(
                    ImageFormat.JPEG);
            Size[] snapshotSizes = new Size[snapshotSizeSet.size()];
            snapshotSizes = (Size[]) snapshotSizeSet.toArray(snapshotSizes);
            Size suggestedMaxJpegSize = getMaxSize(snapshotSizes);

            Set previewSizeSet = snapshotConfig.getOutputSizes(
                    ImageFormat.PRIVATE);
            Size[] previewSizes = new Size[previewSizeSet.size()];
            previewSizes = (Size[]) previewSizeSet.toArray(previewSizes);
            Size suggestedMaxPreviewSize = getMaxSize(previewSizes);
        }

        if (sizes == null || sizes.length == 0) {
            throw new IllegalArgumentException("sizes was empty");
        }

        Size sz = sizes[0];
        for (Size size : sizes) {
            if (size.getWidth() * size.getHeight() > sz.getWidth() * sz.getHeight()) {
                sz = size;
            }
        }

        return sz;
    }

    private void drawFaceRect(Face[] faces) {
        for(Face face : faces){
            if(face.getScore() > 50){
                Log.e(TAG, "drawFaceRect: faces =>> "+face);
                FaceMeteringRect faceMeteringRect = requireView().findViewById(R.id.fm_rect);
                faceMeteringRect.setMeteringRect(new RectF(face.getBounds().left,face.getBounds().top
                        ,face.getBounds().right,face.getBounds().bottom));

            }
        }
    }

    /**
     * C A L L B A C K S
     */

    ImageReader.OnImageAvailableListener snapshotImageCallback = imageReader -> {
        Log.e(TAG, "onImageAvailable: received snapshot image data");
        Image img = imageReader.acquireLatestImage();
        //TODO : FIX THIS
        new Handler(Looper.getMainLooper()).post(new ImageSaverThread(img,cameraId,requireActivity().getContentResolver()));
//        imageReader.close();
    };

    CameraDevice.StateCallback imageCaptureCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            camDevice = cameraDevice;
            try {
                camDevice.createCaptureSession(surfaceList,stateCallback, mBackgroundHandler);
                tvPreview.setAspectRatio(imageSize.getHeight(),imageSize.getWidth());
                Log.e(TAG, "onOpened: tvPreview.SetAspectRatio : h : "+imageSize.getHeight() + " w : "+imageSize.getWidth());

            } catch (Exception e) {
                Log.e(TAG, "onOpened: session create failed: " + e.getMessage());
                camDevice = null;
                requireActivity().finish();
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            Log.e(TAG, "onDisconnected: camera disconnected");
            closeCamera();
            requireActivity().finish();
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            Log.e(TAG, "onError: camera failed: " + i);
            camDevice = null;
            requireActivity().finish();
        }
    };

    CameraCaptureSession.StateCallback stateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
            camSession = cameraCaptureSession;
            try {
                camDeviceCaptureRequest = camDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                camDeviceCaptureRequest.addTarget(surfaceList.get(0));
                camDeviceCaptureRequest.set(CaptureRequest.JPEG_QUALITY,(byte) 100);

                camDeviceCaptureRequest.set(CaptureRequest.EDGE_MODE
                        ,CaptureRequest.EDGE_MODE_HIGH_QUALITY);
//                camDeviceCaptureRequest.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE
//                        ,CaptureRequest.STATISTICS_FACE_DETECT_MODE_SIMPLE);

                //FRONT CAMERA INVERSION FIX
                if(characteristics.get(CameraCharacteristics.LENS_FACING)==CameraCharacteristics.LENS_FACING_FRONT){
                    camDeviceCaptureRequest.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(Surface.ROTATION_180));
                }
                else {
                    camDeviceCaptureRequest.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(0));
                }
                camSession.setRepeatingRequest(camDeviceCaptureRequest.build(), previewCallback, null);
                ready = true;
            } catch (Exception e) {
                Log.e(TAG, "IMAGE CAPTURE CALLBACK: onConfigured: create preview failed: " + e.getMessage());
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
            Log.e(TAG, "IMAGE CAPTURE CALLBACK: onConfigureFailed: configure failed");
        }
    };

    CameraCaptureSession.CaptureCallback previewCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
//            Face[] faces = result.get(CaptureResult.STATISTICS_FACES);
//            Log.e(TAG, "onCaptureCompleted: FACES : "+faces.length);
//            if(faces.length > 0){
//                drawFaceRect(faces);
//            }
        }
        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
            Log.e(TAG, "onCaptureFailed: lost preview");
        }
    };

    CameraCaptureSession.CaptureCallback snapshotCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            super.onCaptureProgressed(session, request, partialResult);
            Log.d(TAG, "onCaptureProgressed() returned:  capture progressed");
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
            Log.e(TAG, "onCaptureFailed: lost snapshot");
        }
    };

    /**
     * LIFECYCLE METHODS
     */

    @Override
    public void onResume() {
        super.onResume();
        resumed = true;
        display_latest_image_from_gallery();
        startBackgroundThread();
        openCamera();
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.e(TAG, "onPause");
        stopBackgroundThread();
        ready = false;
        resumed = false;
        closeCamera();
    }

    private static class CompareSizeByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum( (long)(lhs.getWidth() * lhs.getHeight()) -
                    (long)(rhs.getWidth() * rhs.getHeight()));
        }
    }
}