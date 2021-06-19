package com.uncanny.camx.Data;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.os.Build;
import android.util.Log;
import android.util.Pair;
import android.util.Range;
import android.util.Size;
import android.widget.Toast;

import com.uncanny.camx.Utility.CompareSizeByArea;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LensData {
    private static final String TAG = "LensData";
    private static final String CAMERA_MAIN_BACK = "0";
    private static final String CAMERA_MAIN_FRONT = "1";
    private int LOGICAL_ID;
    Context activity;
    CameraCharacteristics characteristics;
    int[] capabilities;
    CameraManager cameraManager;
    List<Integer> physicalCameras = new ArrayList<>();
    List<Integer> logicalCameras  = new ArrayList<>();
    List<Integer> auxiliaryCameras  = new ArrayList<>();
    Range<Integer>[] highFPSRanges;
    ArrayList<Pair<Size, Range<Integer>>> fpsResolutionPair = new ArrayList<>();
    Map<Range<Integer>, Size[]> slowMoeMap = new HashMap<>();
    LensResolutionData lensResolutionData = new LensResolutionData();

    /**
     * Constructor for this class.
     */
    public LensData(Context activity){
        this.activity = activity;
        getAuxCameras();
    }

    /**
     * Returns boolean for Auxiliary Camera availability.
     * First query aux cam availability then only call
     * {@link LensData#getPhysicalCameras()}.
     */
    public boolean isAuxCameraAvailable(){
        return (auxiliaryCameras.size() != 0);
    }

    /**
     * Returns number of Auxiliary Camera Sensors other than cameraId (0,1).
     */
    public int totalAuxCameras(){
        return auxiliaryCameras.size();
    }

    /**
     * Returns a list of camera Ids for {@link LensData#totalAuxCameras()}.
     */
    public List<Integer> getAuxiliaryCameras(){
        auxiliaryCameras = new ArrayList<>(physicalCameras);
        auxiliaryCameras.remove((Object)0);
        auxiliaryCameras.remove((Object)1);
        return auxiliaryCameras;
    }

    /**
     * Returns total number of Camera Sensors including cameraId (0,1).
     */
    public int totalPhysicalCameras(){
        return physicalCameras.size();
    }

    /**
     * Returns a list of camera Ids for {@link LensData#totalPhysicalCameras()}.
     */
    public List<Integer> getPhysicalCameras(){
        return physicalCameras;
    }

    /**
     * Returns the number of logical cameras (if present)
     * check {@link CameraCharacteristics#getPhysicalCameraIds()}.
     */
    public int totalLogicalCameras(){
        return logicalCameras.size();
    }

    /**
     * Returns a list of camera Ids for {@link LensData#totalLogicalCameras()} ()}.
     */
    public List<Integer> getLogicalCameras(){
        return logicalCameras;
    }

    /**
     * Returns boolean for Camera2api availability.
     */
    public boolean hasCamera2api(){
        Integer c2api = getCameraCharacteristics("0").get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
        if(c2api!=null && c2api == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED){
            Log.e(TAG, "hasCamera2api: HARDWARE_LEVEL_LIMITED");
            return true;
        }
        else if(c2api!=null && c2api == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL){
            Log.e(TAG, "hasCamera2api: HARDWARE_LEVEL_FULL");
            return true;
        }
        else if(c2api!=null && c2api == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY){
            Log.e(TAG, "hasCamera2api: HARDWARE_LEVEL_LEGACY");
            return true;
        }
        else if(c2api!=null && c2api == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3){
            Log.e(TAG, "hasCamera2api: HARDWARE_LEVEL_3");
            return true;
        }
        else if(c2api!=null && c2api == CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL){
            Log.e(TAG, "hasCamera2api: HARDWARE_LEVEL_EXTERNAL");
            return true;
        }
        else{
            return false;
        }
    }

    /**
     * Use preference key to save modes.
     */
    public String[] getAvailableModes(String id){
        List<String> cameraModes = new ArrayList<>();
        capabilities = getCameraCharacteristics(id).get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        cameraModes.add("Camera");
        cameraModes.add("Video");

        if(capabilities!=null && findHSVCapability(capabilities)){
            cameraModes.add("Slo Moe");
            cameraModes.add("TimeWarp");
        }
        cameraModes.add("Portrait");
        cameraModes.add("Night");
        if(hasCamera2api()){
            cameraModes.add("Pro");
        }
        //ADD CHECK FOR MULTI MODE (if there's one)
        return cameraModes.toArray(new String[0]);
    }

    /**
     * Returns Pair of Size and FPS ranges.
     */
    public ArrayList<Pair<Size, Range<Integer>>> getFpsResolutionPair(String id){
        StreamConfigurationMap map = getStreamConfigMap(id);
        for(Range<Integer> range : map.getHighSpeedVideoFpsRanges()){
            if(range.getLower().equals(range.getUpper())) {
                for (Size size : map.getHighSpeedVideoSizesFor(range)) {
                    fpsResolutionPair.add(new Pair<>(size, range));
                }
            }
        }
        return fpsResolutionPair;
    }

    /**
     * DEBUGGING purposes
     */
    public void getCameraLensCharacteristics(String id){
        StreamConfigurationMap map = getStreamConfigMap(id);
        CameraCharacteristics cc = getCameraCharacteristics(id);
        cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
//        Log.e(TAG, "getCameraLensCharacteristics: SLO MO : FPS RANGE : "+ Arrays.toString(map.getHighSpeedVideoFpsRanges()));
//        Log.e(TAG, "getCameraLensCharacteristics: SLO MO : FPS RANGE : "+ Arrays.toString(map.getHighSpeedVideoSizes()));
    }

    /**
     * returns a {@link CamcorderProfile} for camera ids [0,1] only..not recommended.
     * @param id the id for the camera.
     */
    public CamcorderProfile getCamcorderSMProfile(int id, Size size){
        Log.e(TAG, "getCamcorderSMProfile: "+size);
        if(size.getWidth() == 480){
            Log.e(TAG, "getCamcorderSMProfile: 1 : "+CamcorderProfile.get(0,CamcorderProfile.QUALITY_HIGH_SPEED_480P));
            return CamcorderProfile.get(0,CamcorderProfile.QUALITY_HIGH_SPEED_480P);
        }
        if(size.getWidth() == 720){
            Log.e(TAG, "getCamcorderSMProfile: 1 : "+CamcorderProfile.get(0,CamcorderProfile.QUALITY_HIGH_SPEED_720P));
            return CamcorderProfile.get(0,CamcorderProfile.QUALITY_HIGH_SPEED_720P);
        }
        if(size.getWidth() == 1080){
            Log.e(TAG, "getCamcorderSMProfile: 1 : "+CamcorderProfile.get(0,CamcorderProfile.QUALITY_HIGH_SPEED_1080P));
            return CamcorderProfile.get(0,CamcorderProfile.QUALITY_HIGH_SPEED_1080P);
        }
        if(CamcorderProfile.hasProfile(0,CamcorderProfile.QUALITY_HIGH_SPEED_2160P)){
            Log.e(TAG, "getCamcorderSMProfile: 1 : "+CamcorderProfile.get(0,CamcorderProfile.QUALITY_HIGH_SPEED_2160P));
            return CamcorderProfile.get(0,CamcorderProfile.QUALITY_HIGH_SPEED_2160P);
        }
        if(CamcorderProfile.hasProfile(0,CamcorderProfile.QUALITY_HIGH_SPEED_HIGH)){
            Log.e(TAG, "getCamcorderSMProfile: 1 : "+CamcorderProfile.get(0,CamcorderProfile.QUALITY_HIGH_SPEED_HIGH));
            return CamcorderProfile.get(0,CamcorderProfile.QUALITY_HIGH_SPEED_HIGH);
        }
        if(CamcorderProfile.hasProfile(0,CamcorderProfile.QUALITY_HIGH_SPEED_LOW)){
            Log.e(TAG, "getCamcorderSMProfile: 1 : "+CamcorderProfile.get(0,CamcorderProfile.QUALITY_HIGH_SPEED_LOW));
            return CamcorderProfile.get(0,CamcorderProfile.QUALITY_HIGH_SPEED_LOW);
        }
        return CamcorderProfile.get(id,CamcorderProfile.QUALITY_HIGH);
    }

    private void getAuxCameras(){
        for(int i = 0; i<=64 ; i++){
            try {
                cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(String.valueOf(i));
                if (characteristics!=null ) {
                    Log.e(TAG, "check_aux: value of array at " + i + " : " + i);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        if(characteristics.getPhysicalCameraIds().size() > 0){
                            LOGICAL_ID = i;
                            Toast.makeText(activity, "Execution Completed cam_aux() Logical_ID "
                                            +i, Toast.LENGTH_LONG).show();
                        }
                    }
                    if(LOGICAL_ID != 0 && i >= LOGICAL_ID){
                        logicalCameras.add(i);
                    }
                    else  {
                        physicalCameras.add(i);
                    }
                }
            }
            catch (IllegalArgumentException | CameraAccessException ignored){ }
        }
        Toast.makeText(activity, "Execution Completed cam_aux() Physical ids "+physicalCameras, Toast.LENGTH_SHORT).show();
        Toast.makeText(activity, "Execution Completed cam_aux() Logical  ids "+logicalCameras , Toast.LENGTH_SHORT).show();
    }

    private void BayerCheck(int id) {
        StreamConfigurationMap map = getCameraCharacteristics(id+"")
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map.getHighResolutionOutputSizes(ImageFormat.JPEG) != null) {
            Size[] size = (map.getHighResolutionOutputSizes(ImageFormat.JPEG).length > 0 ?
                    map.getHighResolutionOutputSizes(ImageFormat.JPEG) :
                    map.getHighResolutionOutputSizes(ImageFormat.RAW_SENSOR));
            if (size.length > 0) {
                lensResolutionData.setBayer(true);
                ArrayList<Size> sizeArrayList = new ArrayList<>(Arrays.asList(size));
                Size mSize = Collections.max(sizeArrayList, new CompareSizeByArea());
                lensResolutionData.setBayerPhotoSize(mSize);
//                Log.e(TAG, "BayerCheck: BAYER SENSOR SIZE : " + Arrays.toString(size) + " mSize : " + mSize);
            } else {
                lensResolutionData.setBayer(false);
                Log.e(TAG, "BayerCheck: NOT BAYER : ID : " + id);
            }
        }
    }

    private static boolean findHSVCapability(int[] capabilities) {
        for(int caps : capabilities){
            if(caps == CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO) return true;
        }
        return false;
    }

    private CameraCharacteristics getCameraCharacteristics(String camId) {
        cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            characteristics = cameraManager.getCameraCharacteristics(camId);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return characteristics;
    }

    private StreamConfigurationMap getStreamConfigMap(String id){
        return getCameraCharacteristics(id)
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
    }

}