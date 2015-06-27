package com.martinandersson.nearbytweets;

import android.location.Location;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.twitter.sdk.android.Twitter;
import com.twitter.sdk.android.core.AppSession;
import com.twitter.sdk.android.core.Callback;
import com.twitter.sdk.android.core.Result;
import com.twitter.sdk.android.core.TwitterApiClient;
import com.twitter.sdk.android.core.TwitterAuthConfig;
import com.twitter.sdk.android.core.TwitterCore;
import com.twitter.sdk.android.core.TwitterException;
import com.twitter.sdk.android.core.models.Search;
import com.twitter.sdk.android.core.models.Tweet;
import com.twitter.sdk.android.core.services.SearchService;
import com.twitter.sdk.android.core.services.params.Geocode;
import com.twitter.sdk.android.tweetui.TweetViewAdapter;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import io.fabric.sdk.android.Fabric;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    // Note: Your consumer key and secret should be obfuscated in your source code before shipping.
    private static final String TWITTER_KEY = "cwYlodNmgA5veS0EShHFl2yQm";
    private static final String TWITTER_SECRET = "JePHisPGIM3Lr2AM0lBznkWJduCFrvKItjZgtaI418ISMcNysP";

    public static final String TAG = MainActivity.class.getSimpleName();

    @InjectView(R.id.search_text)
    EditText mSearchText;

    @InjectView(R.id.search_button)
    Button mSearchButton;

    @InjectView(R.id.tweet_listview)
    ListView mTweetListview;

    private TweetViewAdapter mAdapter;
    private GoogleApiClient mGoogleApiClient;
    private Location mLastLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TwitterAuthConfig authConfig = new TwitterAuthConfig(TWITTER_KEY, TWITTER_SECRET);
        Fabric.with(this, new Twitter(authConfig));
        setContentView(R.layout.activity_main);
        ButterKnife.inject(this);

        mSearchText.setText("");
        mSearchText.setSelection(mSearchText.getText().length());

        mAdapter = new TweetViewAdapter(this, new ArrayList<>());
        mTweetListview.setAdapter(mAdapter);

        mSearchText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    searchOnTwitter();
                    return true;
                }
                return false;
            }
        });

        buildGoogleApiClient();
        logInGuestToTwitter();
    }

    private void logInGuestToTwitter() {
        TwitterCore.getInstance().logInGuest(new Callback<AppSession>() {
            @Override
            public void success(Result appSessionResult) {
                // REST API REQUEST...
                Log.d(TAG, "logInGuest - success");
                searchOnTwitter();
            }

            @Override
            public void failure(TwitterException e) {
                // OOPS
                Log.d(TAG, "logInGuest - failure");

            }
        });

    }

    @OnClick(R.id.search_button)
    public void searchOnTwitter() {
        // Hide keyboard
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(mSearchText.getWindowToken(), 0);

        // Setup search query
        String searchTerm = mSearchText.getText().toString();
        String query = null;
        Geocode geocode = new Geocode(mLastLocation.getLatitude(), mLastLocation.getLongitude(), 10, Geocode.Distance.MILES);
        try {
            query = URLEncoder.encode(searchTerm, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        Log.d(TAG, "searchOnTwitter: " + query + ", " + mLastLocation.getLatitude() + ", " + mLastLocation.getLongitude());

        // Perform twitter search
        TwitterApiClient twitterApiClient = TwitterCore.getInstance().getApiClient();
        SearchService searchService = twitterApiClient.getSearchService();
        searchService.tweets(query, geocode, null, null, "recent", 20,
                null, null, null, true,
                new Callback<Search>() {
                    @Override
                    public void success(Result<Search> searchResult) {
                        Search results = searchResult.data;
                        List<Tweet> tweets = results.tweets;
                        Log.d(TAG, "tweets -> success: " + tweets.size());
                        mAdapter.setTweets(tweets);
                    }

                    @Override
                    public void failure(TwitterException e) {
                        Log.d(TAG, "tweets -> failure: " + e.getMessage());
                    }
                });
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    /**
     * Runs when a GoogleApiClient object successfully connects.
     */
    @Override
    public void onConnected(Bundle connectionHint) {
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        if (mLastLocation != null) {
            String text = "Lat: " + mLastLocation.getLatitude() + "\nLon: " + mLastLocation.getLongitude();
            Log.d(TAG, "onConnected: " + text);
        } else {
            Toast.makeText(this, R.string.no_location_detected, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onConnectionFailed(ConnectionResult result) {
        Log.w(TAG, "onConnectionFailed: " + result.getErrorCode());
        // Refer to the javadoc for ConnectionResult to see what error codes might be returned in onConnectionFailed.
    }

    @Override
    public void onConnectionSuspended(int cause) {
        Log.w(TAG, "onConnectionSuspended");
        // The connection to Google Play services was lost for some reason. We call connect() to attempt to re-establish the connection.
        mGoogleApiClient.connect();
    }
}
