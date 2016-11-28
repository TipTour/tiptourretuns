package mountainq.helloegg.tiptourguide;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.skp.Tmap.TMapData;
import com.skp.Tmap.TMapGpsManager;
import com.skp.Tmap.TMapMarkerItem;
import com.skp.Tmap.TMapPoint;
import com.skp.Tmap.TMapPolyLine;
import com.skp.Tmap.TMapView;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;

import mountainq.helloegg.tiptourguide.activities.NavigationDrawerActivity;
import mountainq.helloegg.tiptourguide.adapters.TourSearchAdapter;
import mountainq.helloegg.tiptourguide.data.BooleanData;
import mountainq.helloegg.tiptourguide.data.PushDatas;
import mountainq.helloegg.tiptourguide.data.PushGuideToTourist;
import mountainq.helloegg.tiptourguide.data.SearchKeyword;
import mountainq.helloegg.tiptourguide.data.StaticData;
import mountainq.helloegg.tiptourguide.data.TourBoxItem;
import mountainq.helloegg.tiptourguide.interfaces.NetworkService;
import mountainq.helloegg.tiptourguide.manager.DropManager;
import mountainq.helloegg.tiptourguide.manager.LOG;
import mountainq.helloegg.tiptourguide.parsers.SearchKeySAXParser;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class MainActivity extends NavigationDrawerActivity implements TMapGpsManager.onLocationChangedCallback {
    private StaticData mData = StaticData.getInstance();

    //UI
    private ListView searchList;
    private TMapView tMapView = null;
    private EditText searchEt;
    ImageView searchBtn;
    private RelativeLayout tMapLayout;
    FrameLayout mapFrame;

    //BottomSheet
    private RelativeLayout bottomSheet;
    private TextView startPointText, endPointText, timeText, distanceText, costText;
    Button saveBtn, bottomSheetCloseButton;


    //Data
    private ArrayList<SearchKeyword> items = new ArrayList<>();
    SearchKeyword startPoint;
    SearchKeyword endPoint;
    private TourSearchAdapter searchAdapter;

    //Controller
    ApplicationController app;
    NetworkService networkService;
    TMapGpsManager gps = null;
    private boolean isMapVisible = true;
    private boolean isBottomSheetVisible = true;
    double lon, lat;
    String code = "", idx = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState, R.layout.activity_main);

        tMapLayout = (RelativeLayout) findViewById(R.id.relative_map);
        mContext = this;
        app = (ApplicationController) getApplicationContext();
        networkService = app.getNetworkService();

        /**
         * 푸시 데이터 받아오기
         */
        Intent bundleData = getIntent();
        code = bundleData.getStringExtra("code");
        idx = bundleData.getStringExtra("idx");

        if (code != null && !code.equals("")) {
            switch (code) {
                case StaticData.GUIDE:
                    getGuidePushData(Integer.parseInt(idx));
                    break;
                case StaticData.CUSTOMER_YES:
                    getCustomerYesPushData(Integer.parseInt(idx));
                    break;
                case StaticData.CUSTOMER_NO:
                    getCustomerNoPushData(Integer.parseInt(idx));
                    break;
            }
        }
        initialize();
        createTmap(this);
//        drawPoint(new SearchKeyword(String.valueOf(StaticData.DUMMY_LON + 0.000023),String.valueOf(StaticData.DUMMY_LAT+0.000023),"sample"));
    }

    /**
     * 버튼 클릭 리스너 버튼을 누르면 액티비티가 짜쟈쟌
     *
     * @param v
     */
    public void onMenuClickListener(View v) {
        if (v.getId() == R.id.g1_menu1) {
            closeDrawer();
            return;
        }

        Intent intent = getActivity(v.getId());
        String toolbarString = ((TextView) findViewById(v.getId())).getText().toString();
        intent.putExtra("text", toolbarString);

        startActivity(intent);

        Log.d("Test", "menu clicked");
        closeDrawer();
    }

    /**
     * 버튼에 따른 인텐트 생성
     *
     * @param resId
     * @return
     */
    private Intent getActivity(int resId) {
        Intent intent = null;
        switch (resId) {
            case R.id.g1_menu2:
                intent = new Intent(MainActivity.this, TourBoxActivity.class);
                break;
            case R.id.g2_menu1:
                intent = new Intent(MainActivity.this, ProfileSettingActivity.class);
                break;
            case R.id.g2_menu2:
                intent = new Intent(MainActivity.this, GuideSettingActivity.class);
                break;
            case R.id.g2_menu3:
                intent = new Intent(MainActivity.this, InformationActivity.class);
                break;
        }
        Log.d("Test", "intent : " + intent.toString());
        return intent;
    }

    /**
     * 레이아웃 초기화
     */
    private void initialize() {
        Log.d("Test", "initialize");
        RelativeLayout.LayoutParams llp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, mData.getHeight() / 4);
        bottomSheet = (RelativeLayout) findViewById(R.id.bottom);
        bottomSheet.setLayoutParams(llp);
        mapFrame = (FrameLayout) findViewById(R.id.mapFrame);

        saveBtn = (Button) findViewById(R.id.saveBtn);
        saveBtn.setOnClickListener(onSaveBtnClickListener);

        startPointText = (TextView) findViewById(R.id.startPointText);
        endPointText = (TextView) findViewById(R.id.endPointText);
        timeText = (TextView) findViewById(R.id.timeText);
        distanceText = (TextView) findViewById(R.id.distanceText);
        costText = (TextView) findViewById(R.id.costText);

        searchList = (ListView) findViewById(R.id.searchlist);
        searchEt = (EditText) findViewById(R.id.searchet);
        searchBtn = (ImageView) findViewById(R.id.searchbtn);
        searchBtn.setOnClickListener(searchBtnClickListener);
        bottomSheetCloseButton = (Button) findViewById(R.id.bottomSheetCloseBtn);
        bottomSheetCloseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideBottomSheet();
            }
        });

        searchEt.addTextChangedListener(searchEditTextWatcher);
        searchAdapter = new TourSearchAdapter(items, this);


        searchList.setAdapter(searchAdapter);
        searchList.setOnItemClickListener(searchItemClickLIstener);

        gps = new TMapGpsManager(this);
        gps.setMinTime(1000);
        gps.setMinDistance(5);
        gps.setProvider(TMapGpsManager.GPS_PROVIDER);
        gps.OpenGps();

//        createTmap();
    }

    /**
     * 위치가 바뀔 때마다 좌표값을 저장해두는 메소드
     *
     * @param location
     */
    @Override
    public void onLocationChange(Location location) {
        lat = location.getLatitude();
        lon = location.getLongitude();
    }


    /**
     * Tmap 을 생성하는 메소드 모아놓음
     */
    private void createTmap(Context context) {

        try {
            LOG.DEBUG("this : " + context.toString() + " tMap is created");
            tMapView = new TMapView(context);
            tMapLayout.addView(tMapView);

        } catch (NullPointerException e) {
            LOG.ERROR(e.getMessage());
            e.printStackTrace();
        }
        Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.location_me);
        if (tMapView != null) {
            tMapView.setSKPMapApiKey("5f0bdfc2-2f1f-365a-9a25-e49f12151a82");
            tMapView.setLanguage(TMapView.LANGUAGE_KOREAN);
            tMapView.setIconVisibility(true);
            tMapView.setZoomLevel(7);
            tMapView.setMapType(TMapView.MAPTYPE_STANDARD);
            tMapView.setCompassMode(false);
            tMapView.setTrackingMode(true);
            tMapView.setIcon(icon);
            tMapView.setLocationPoint(lon, lat);
//            tMapView.setLocationPoint(StaticData.DUMMY_LON, StaticData.DUMMY_LAT); //dummy data
        }
    }

    double distance = 0.0;


    /**
     * 두 지점을 연결해서 길로 이어주는 메소드
     *
     * @param start 시작점
     * @param end   끝점
     * @return 길이가 반환된다.
     */
    private double drawStratToEnd(SearchKeyword start, SearchKeyword end) {
        LOG.DEBUG("go to start " + start.toString() + " end" + end.toString());
        double dist = 0.0f;
        double startX = start.getMapx();
        double startY = start.getMapy();
        double endX = end.getMapx();
        double endY = end.getMapy();

        final TMapData tMapData = new TMapData();
        final TMapPoint startPoint = new TMapPoint(startX, startY);
        final TMapPoint endPoint = new TMapPoint(endY, endX);

        tMapData.findPathDataWithType(TMapData.TMapPathType.PEDESTRIAN_PATH, startPoint, endPoint, new TMapData.FindPathDataListenerCallback() {
            @Override
            public void onFindPathData(TMapPolyLine tMapPolyLine) {
                LOG.DEBUG("poly line is created");
                tMapView.addTMapPath(tMapPolyLine);
                tMapView.setZoomLevel(15);
                distance = tMapPolyLine.getDistance();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        timeText.setText(DropManager.calTime(distance));
                        distanceText.setText(DropManager.calDistance(distance));
                        costText.setText(DropManager.calCost(distance));
                    }
                });
            }
        });
        return distance;
    }

    /**
     * 점을 찍어서 위치를 보여주는 메소드
     *
     * @param point 지점을 넣는다.
     */
    private void drawPoint(SearchKeyword point) {
        Bitmap icon = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
        TMapPoint tMapPoint = new TMapPoint(point.getMapy(), point.getMapx());
        TMapMarkerItem marker = new TMapMarkerItem();
        marker.setTMapPoint(tMapPoint);
        marker.setName(point.getTitle());
        marker.setVisible(TMapMarkerItem.VISIBLE);
        marker.setIcon(icon);
        tMapView.addMarkerItem("point", marker);
    }


    /**
     * 검색을 할때 리스트가 보이게 함
     * <p>
     * 검색어를 입력하는 순간 상단에 검색창이 바뀜
     */
    private TextWatcher searchEditTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            searchEt.setBackgroundColor(StaticData.WHITE_TEXT_COLOR);
            searchEt.setTextColor(0xff000000);
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            hideBottomSheet();
        }
    };


    private View.OnClickListener searchBtnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Log.d("Test", "searching button clicked");
            new SearchTask().execute(searchEt.getText().toString());
        }
    };

    private AdapterView.OnItemClickListener searchItemClickLIstener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            showTmap();
            showBottomSheet();
            startPointText.setText("내위치");
            startPoint = new SearchKeyword(String.valueOf(lon), String.valueOf(lat), "내 위치");
//            startPoint = new SearchKeyword("37.568477", "126.981611", "내 위치");
            endPoint = new SearchKeyword(items.get(position));
            drawStratToEnd(startPoint, endPoint);
//            drawPoint(endPoint);
            endPointText.setText(endPoint.getTitle());
        }
    };

    private void showBottomSheet() {
        bottomSheet.setVisibility(View.VISIBLE);
        isBottomSheetVisible = true;
    }

    private void hideBottomSheet() {
        bottomSheet.setVisibility(View.GONE);
        isBottomSheetVisible = false;
    }

    private void showTmap() {
        tMapLayout.setVisibility(View.VISIBLE);
        searchList.setVisibility(View.GONE);
        isMapVisible = true;
    }

    private void showList() {
        LOG.DEBUG("show list is visible!");
        tMapLayout.setVisibility(View.GONE);
        searchList.setVisibility(View.VISIBLE);
        isMapVisible = false;
    }

    private View.OnClickListener onSaveBtnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            AlertDialog dialog = new AlertDialog.Builder(mContext)
                    .setTitle("알림")
                    .setMessage("연결하시겠습니까?")
                    .setPositiveButton("찾기", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ConnectTourGuide();
                            mData.setTempPushDatas(new PushDatas(-1, startPoint.getMapx(), startPoint.getMapy(), endPoint.getMapx(), endPoint.getMapy(), startPoint.getTitle(), endPoint.getTitle(), app.user_id));
                            dialog.dismiss();
                        }
                    })
                    .setNegativeButton("취소", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    }).create();
            dialog.show();

        }
    };

    private void ConnectTourGuide() {
        Intent intent = new Intent(MainActivity.this, GuideListActivity.class);
        startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        if (!isMapVisible) {
            showTmap();
        } else if (isBottomSheetVisible) {
            hideBottomSheet();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * 만든 하나의 트립을 저장하는 메소드
     */
    private void SaveNewTrip() {
        NetworkService networkService = app.getNetworkService();
        TourBoxItem item = new TourBoxItem(
                endPoint.getTitle(),
                distance,
                distance * 1.3,
                (int) (distance / 1.2)
        );
        Call<TourBoxItem> tourBoxItemCall = networkService.newTrip(item);

        tourBoxItemCall.enqueue(new Callback<TourBoxItem>() {
            @Override
            public void onResponse(Call<TourBoxItem> call, Response<TourBoxItem> response) {
                if (response.isSuccessful())
                    Toast.makeText(mContext, "정상적으로 올라갔습니다.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(Call<TourBoxItem> call, Throwable t) {
                Toast.makeText(mContext, "정상적으로 올라가지 않았습니다.", Toast.LENGTH_SHORT).show();
            }
        });
    }


    /**
     * 여행정보를 가져오며 가이드에게 묻는 알람 화면을 띄운다
     *
     * @param idx
     */
    private void getGuidePushData(int idx) {

        Call<PushDatas> pushToGuideCall = networkService.getGuidePushData(idx);
        pushToGuideCall.enqueue(new Callback<PushDatas>() {
            @Override
            public void onResponse(Call<PushDatas> call, Response<PushDatas> response) {
                if (response.isSuccessful()) {
                    final PushDatas pushToGuide = response.body();
                    LOG.DEBUG(pushToGuide.toString());
                    LOG.DEBUG(response.body().toString());
                    final SearchKeyword startPoint = new SearchKeyword(pushToGuide.getStartPointX(), pushToGuide.getStartPointY(), pushToGuide.getStartTitle());
                    SearchKeyword endPoint = new SearchKeyword(pushToGuide.getEndPointX(), pushToGuide.getEndPointY(), pushToGuide.getEndTitle());

                    double dist = drawStratToEnd(startPoint, endPoint);

                    View contentView = View.inflate(mContext, R.layout.fragment_tourselection_guide_bottom, null);
                    ((TextView) contentView.findViewById(R.id.startPointText2)).setText(pushToGuide.getStartTitle());
                    ((TextView) contentView.findViewById(R.id.endPointText2)).setText(pushToGuide.getEndTitle());
                    ((TextView) contentView.findViewById(R.id.timeText2)).setText(DropManager.calTime(dist));
                    ((TextView) contentView.findViewById(R.id.distanceText2)).setText(DropManager.calDistance(dist));
                    ((TextView) contentView.findViewById(R.id.costText2)).setText(DropManager.calCost(dist));
                    LOG.ERROR(pushToGuide.toString());
                    AlertDialog dialog = new AlertDialog.Builder(mContext)
                            .setTitle("가이드를 받으시겠습니까?")
                            .setNegativeButton("안할래요", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    pushToTourist(false, pushToGuide.getTouristid(), pushToGuide.getId());
                                    Toast.makeText(mContext, "거절되었습니다.", Toast.LENGTH_SHORT).show();
                                    dialog.dismiss();
                                    tMapView.removeAllTMapPolyLine();
                                }
                            })
                            .setPositiveButton("할래요", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    pushToTourist(true, pushToGuide.getTouristid(), pushToGuide.getId());
//                                    Toast.makeText(mContext, "수락되었습니다.", Toast.LENGTH_SHORT).show();
                                    dialog.dismiss();
                                    //투어리스트 위치 보여주기
                                    drawPoint(startPoint);
                                }
                            })
                            .setView(contentView)
                            .create();


                    dialog.show();
                }
            }

            @Override
            public void onFailure(Call<PushDatas> call, Throwable t) {

            }
        });
    }

    private void pushToTourist(boolean isAgree, int touristid, int tourid) {

        PushGuideToTourist item = null;
        if (isAgree) item = new PushGuideToTourist(touristid, Integer.parseInt(StaticData.CUSTOMER_YES), tourid);
        else item = new PushGuideToTourist(touristid, Integer.parseInt(StaticData.CUSTOMER_NO), tourid);
        LOG.ERROR("pusht to tourist item : " + item.toString());
        Call<BooleanData> pushGuideToTourist = networkService.pushToTourist(item);
        pushGuideToTourist.enqueue(new Callback<BooleanData>() {
            @Override
            public void onResponse(Call<BooleanData> call, Response<BooleanData> response) {
                if(response.isSuccessful()){

                }
            }

            @Override
            public void onFailure(Call<BooleanData> call, Throwable t) {
                LOG.ERROR(t.getMessage());
            }
        });
    }

    /**
     * 가이드가 수락했으니까 가이드가 오고 있다는 알림창과 함께 가이드 정보를 보여주자
     * 나중에는 소켓 통신을 시작한다.
     *
     * @param idx
     */
    private void getCustomerYesPushData(int idx) {
        Call<PushDatas> pushDatasCall = networkService.getGuidePushData(idx);
        LOG.ERROR("getCustomerYesPushData : " + idx);
        pushDatasCall.enqueue(new Callback<PushDatas>() {
            @Override
            public void onResponse(Call<PushDatas> call, Response<PushDatas> response) {
                if(response.isSuccessful()){
                    tMapView.removeAllTMapPolyLine();
                    PushDatas result = response.body();
                    SearchKeyword startPoint = new SearchKeyword(result.getStartPointX(), result.getStartPointY(), result.getStartTitle());
                    SearchKeyword endPoint = new SearchKeyword(result.getEndPointX(), result.getEndPointY(), result.getEndTitle());
                    drawStratToEnd(startPoint, endPoint);

                }
            }

            @Override
            public void onFailure(Call<PushDatas> call, Throwable t) {

            }
        });
    }

    /**
     * 가이드가 거절한 여행이기 때문에 첨부터 다시하라는 알림창을 띄어주자
     *
     * @param idx
     */
    private void getCustomerNoPushData(final int idx) {
        LOG.ERROR("getCustomerNoPushData : " + idx);

        AlertDialog dialog = new AlertDialog.Builder(mContext)
                .setTitle("거절되었습니다. 다른 가이드를 찾아보시겠습니까?")
                .setPositiveButton("다른 가이드 찾기", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        moveGuideListActivity(idx);
                    }
                })
                .setNegativeButton("여행 취소하기", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        tMapView.removeAllTMapPolyLine();
                        hideBottomSheet();
                    }
                })
                .create();
        dialog.show();
    }

    private void moveGuideListActivity(int idx){
        Call<PushDatas> pushDatasCall = networkService.getGuidePushData(idx);
        pushDatasCall.enqueue(new Callback<PushDatas>() {
            @Override
            public void onResponse(Call<PushDatas> call, Response<PushDatas> response) {
                if(response.isSuccessful()){
                    mData.setTempPushDatas(response.body());
                    ConnectTourGuide();
                }

            }

            @Override
            public void onFailure(Call<PushDatas> call, Throwable t) {

            }
        });
    }


    /**
    * 데이터 통신 관광 API로부터 관광지 정보를 받아와 리스트로 출력한다.
     */
    class SearchTask extends AsyncTask<String, Integer, Void> {

        private URL url = null;
        HttpURLConnection connection = null;

        @Override
        protected Void doInBackground(String... params) {
            String keyword = "";
            Log.d("Test", "SearchTask is doing");
            try {
                keyword = URLEncoder.encode(params[0], "UTF-8");
            } catch (IOException e) {
                e.printStackTrace();
            }

            try {
                url = new URL(StaticData.TOUR_API_SEARCH_URL + keyword);
                connection = (HttpURLConnection) url.openConnection();
                int code = connection.getResponseCode();
                switch (code) {
                    case 200:
                        SearchKeySAXParser parser = new SearchKeySAXParser();
                        items = parser.parse(connection.getInputStream());
                        break;
                    default:
                        break;
                }
            } catch (NullPointerException e) {
                e.printStackTrace();
                Log.d("test", connection.getErrorStream().toString());
            } catch (Exception e) {
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            searchAdapter = new TourSearchAdapter(items, mContext);
            searchList.setAdapter(searchAdapter);
            Log.d("Test", "parsed item : " + items.toString());
            showList();
            searchEt.setTextColor(StaticData.WHITE_TEXT_COLOR);
            searchEt.setBackgroundColor(StaticData.MAIN_COLOR);

            View v = getCurrentFocus();
            if (v != null) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
        }
    }

}
