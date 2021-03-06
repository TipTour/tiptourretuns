package mountainq.helloegg.tiptourguide.fragments;

import android.content.Context;
import android.content.DialogInterface;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
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

import mountainq.helloegg.tiptourguide.ApplicationController;
import mountainq.helloegg.tiptourguide.R;
import mountainq.helloegg.tiptourguide.activities.FChildActivity;
import mountainq.helloegg.tiptourguide.adapters.TourSearchAdapter;
import mountainq.helloegg.tiptourguide.data.SearchKeyword;
import mountainq.helloegg.tiptourguide.data.StaticData;
import mountainq.helloegg.tiptourguide.data.TourBoxItem;
import mountainq.helloegg.tiptourguide.interfaces.NetworkService;
import mountainq.helloegg.tiptourguide.parsers.SearchKeySAXParser;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Created by dnay2 on 2016-11-17.
 *
 */

public class Fragment5_TourSelection extends FChildActivity implements TMapGpsManager.onLocationChangedCallback {

    private StaticData mData = StaticData.getInstance();
    private ListView searchList;
    private TMapView tMapView;
    private EditText searchEt;
    private ImageView searchBtn;
    private RelativeLayout bottomSheet;
    private TextView startPointText, endPointText, timeText, distanceText, costText;
    private Button saveBtn;

    private ArrayList<SearchKeyword> items = new ArrayList<>();
    private SearchKeyword startPoint;
    private SearchKeyword endPoint;
    private TourSearchAdapter searchAdapter;

    ApplicationController app;

    TMapGpsManager gps = null;

    private boolean isMapVisible = true;
    private boolean isFirst = true;

    private RelativeLayout tMapLayout;

    double lon, lat;

    Animation up, down;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = (ApplicationController) getActivity().getApplicationContext();
        up = AnimationUtils.loadAnimation(app.getApplicationContext(), R.anim.d_c);
        down = AnimationUtils.loadAnimation(app.getApplicationContext(), R.anim.c_d);
        gps = new TMapGpsManager(app.getApplicationContext());
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_tourselection, container, false);
        initialize(v);

        return v;
    }

    private void initialize(View v) {

        RelativeLayout.LayoutParams llp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, mData.getHeight() / 7);
        bottomSheet = (RelativeLayout) v.findViewById(R.id.bottom);
        bottomSheet.setLayoutParams(llp);

        saveBtn = (Button) v.findViewById(R.id.saveBtn);
        saveBtn.setOnClickListener(onSaveBtnClickListener);

        startPointText = (TextView) v.findViewById(R.id.startPointText);
        endPointText = (TextView) v.findViewById(R.id.endPointText);
        timeText = (TextView) v.findViewById(R.id.timeText);
        distanceText = (TextView) v.findViewById(R.id.distanceText);
        costText = (TextView) v.findViewById(R.id.costText);

        tMapLayout = (RelativeLayout) v.findViewById(R.id.relative_map);

        new TmapTask().execute();

        searchList = (ListView) v.findViewById(R.id.searchlist);
        searchEt = (EditText) v.findViewById(R.id.searchet);
        searchBtn = (ImageView) v.findViewById(R.id.searchbtn);

        searchEt.addTextChangedListener(searchEditTextWatcher);
        searchAdapter = new TourSearchAdapter(items, app.getApplicationContext());
        searchBtn.setOnClickListener(searchBtnClickListener);

        searchList.setAdapter(searchAdapter);
        searchList.setOnItemClickListener(searchItemClickLIstener);

        gps.setMinTime(1000);
        gps.setMinDistance(5);
        gps.setProvider(TMapGpsManager.GPS_PROVIDER);
        gps.OpenGps();

    }

    @Override
    public void onLocationChange(Location location) {
        lat = location.getLatitude();
        lon = location.getLongitude();
    }

    /**
     * 최초 티맵을 만드는 메소드
     */
    private void createTmap() {

        tMapView = new TMapView(app.getApplicationContext());

        if (tMapView != null) {
            tMapView.setSKPMapApiKey("52141bc0-08fa-3309-af84-0cfc120c0101");
            tMapView.setLanguage(TMapView.LANGUAGE_KOREAN);
            tMapView.setIconVisibility(true);
            tMapView.setZoomLevel(7);
            tMapView.setMapType(TMapView.MAPTYPE_STANDARD);
            tMapView.setCompassMode(false);
            tMapView.setTrackingMode(true);
            tMapLayout.addView(tMapView);
        }

    }

    double distance = 0.0;

    /**
     * 두 지점을 연결해서 길로 이어주는 메소드
     * @param start 시작점
     * @param end 끝점
     * @return 길이가 반환된다.
     */
    private double drawStratToEnd(SearchKeyword start, SearchKeyword end) {
        double startX = start.getMapx();
        double startY = start.getMapy();
        double endX = end.getMapx();
        double endY = end.getMapy();

        final TMapData tMapData = new TMapData();
        final TMapPoint startPoint = new TMapPoint(startY, startX);
        final TMapPoint endPoint = new TMapPoint(endY, endX);

        tMapData.findPathDataWithType(TMapData.TMapPathType.PEDESTRIAN_PATH, startPoint, endPoint, new TMapData.FindPathDataListenerCallback() {
            @Override
            public void onFindPathData(TMapPolyLine tMapPolyLine) {
                tMapView.addTMapPath(tMapPolyLine);
                distance = tMapPolyLine.getDistance();
            }
        });

        return distance;
    }

    /**
     * 점을 찍어서 위치를 보여주는 메소드
     * @param point 지점을 넣는다.
     */
    private void drawPoint(SearchKeyword point) {
        TMapPoint tMapPoint = new TMapPoint(point.getMapy(), point.getMapx());
        TMapMarkerItem marker = new TMapMarkerItem();
        marker.setTMapPoint(tMapPoint);
        tMapView.addMarkerItem("point", marker);
    }


    /**
     * 검색을 할때 리스트가 보이게 함
     *
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

    private View.OnTouchListener onTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {

            return false;
        }
    };


    private void showTmap() {
        tMapLayout.setVisibility(View.VISIBLE);
        searchList.setVisibility(View.GONE);
        isMapVisible = true;
    }

    private void showList() {
        tMapLayout.setVisibility(View.GONE);
        searchList.setVisibility(View.VISIBLE);
        isMapVisible = false;

    }


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
            startPoint = new SearchKeyword(/*String.valueOf(lon), String.valueOf(lat),*/"37.568477", "126.981611", "내 위치", "");
            endPoint = new SearchKeyword(items.get(position));
            drawStratToEnd(startPoint, endPoint);
            endPointText.setText(endPoint.getTitle());
            timeText.setText(String.valueOf(((int) distance / 1.2)));
            distanceText.setText(String.valueOf(((int) distance)));
            costText.setText(String.valueOf(((int) distance * 1.3)));

        }
    };

    private void showBottomSheet(){
        bottomSheet.startAnimation(up);
        bottomSheet.setVisibility(View.VISIBLE);
    }

    private void hideBottomSheet(){
        bottomSheet.startAnimation(down);
        bottomSheet.setVisibility(View.GONE);
    }

    private View.OnClickListener onSaveBtnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            AlertDialog dialog = new AlertDialog.Builder(getActivity())
                    .setTitle("알림")
                    .setMessage("연결하시겠습니까?")
                    .setPositiveButton("저장", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            SaveNewTrip();
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


    private void ConnectTourGuide(){

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
                    Toast.makeText(getActivity(), "정상적으로 올라갔습니다.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(Call<TourBoxItem> call, Throwable t) {
                Toast.makeText(getActivity(), "정상적으로 올라가지 않았습니다.", Toast.LENGTH_SHORT).show();
            }
        });
    }


    /**
     * 티맵아 좀 되라
     */
    class TmapTask extends AsyncTask<Void, Void, Void>{
        int cnt = 0;
        @Override
        protected Void doInBackground(Void... params) {
            while(tMapView == null && cnt < 10) {
                publishProgress();
                cnt++;
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Void... values) {
            createTmap();
            Log.d("Test", "돌자");
        }
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
            } catch (Exception e){
                e.printStackTrace();
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            searchAdapter = new TourSearchAdapter(items, app.getApplicationContext());
            searchList.setAdapter(searchAdapter);
            Log.d("Test", "parsed item : " + items.toString());
            showList();
            searchEt.setTextColor(StaticData.WHITE_TEXT_COLOR);
            searchEt.setBackgroundColor(StaticData.MAIN_COLOR);

            View v = getActivity().getCurrentFocus();
            if(v != null){
                InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            }
        }
    }


}
