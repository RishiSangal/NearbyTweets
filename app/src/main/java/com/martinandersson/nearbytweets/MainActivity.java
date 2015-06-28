package com.martinandersson.nearbytweets;

import android.location.Location;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.twitter.sdk.android.Twitter;
import com.twitter.sdk.android.core.AppSession;
import com.twitter.sdk.android.core.Callback;
import com.twitter.sdk.android.core.Result;
import com.twitter.sdk.android.core.TwitterApiClient;
import com.twitter.sdk.android.core.TwitterApiException;
import com.twitter.sdk.android.core.TwitterAuthConfig;
import com.twitter.sdk.android.core.TwitterCore;
import com.twitter.sdk.android.core.TwitterException;
import com.twitter.sdk.android.core.internal.TwitterApiConstants;
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
    public static final String KEY_TWEETS = "KEY_TWEETS";
    public static final int TWITTER_SEARCH_RADIUS_MILES = 20;
    public static final int MAX_NUMBER_OF_TWEETS = 50;

    @InjectView(R.id.search_text)
    EditText mSearchText;
    @InjectView(R.id.tweet_listview)
    ListView mTweetListview;
    @InjectView(R.id.no_results)
    TextView mNoResults;
    @InjectView(R.id.progress_bar)
    ProgressBar mProgressBar;
    @InjectView(R.id.failed_to_get_tweets)
    TextView mFailedToGetTweets;

    private TweetViewAdapter mAdapter;
    private GoogleApiClient mGoogleApiClient;
    private Location mLastLocation;
    private List<Tweet> mTweets = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TwitterAuthConfig authConfig = new TwitterAuthConfig(TWITTER_KEY, TWITTER_SECRET);
        Fabric.with(this, new Twitter(authConfig));
        setContentView(R.layout.activity_main);
        ButterKnife.inject(this);

        mSearchText.setText("");
        mSearchText.setSelection(mSearchText.getText().length());

        // Check if we have data to display (after rotation)
        if (savedInstanceState != null) {
            mTweets = new Gson().fromJson(savedInstanceState.getString(KEY_TWEETS), new TypeToken<List<Tweet>>() {
            }.getType());
        } else {
            mProgressBar.setVisibility(View.VISIBLE);
        }

        mAdapter = new TweetViewAdapter(this, mTweets);
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

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_TWEETS, new Gson().toJson(mTweets));
    }

    private void logInGuestToTwitter() {
        TwitterCore.getInstance().logInGuest(new Callback<AppSession>() {
            @Override
            public void success(Result appSessionResult) {
                // REST API REQUEST...
                Log.d(TAG, "logInGuest - success");
                if (mTweets == null || mTweets.size() == 0) {
                    searchOnTwitter();
                } else {
                    Log.d(TAG, "No need to search twitter");
                }
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
        try {
            query = URLEncoder.encode(searchTerm, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        Geocode geocode = null;
        if (mLastLocation != null) {
            geocode = new Geocode(mLastLocation.getLatitude(), mLastLocation.getLongitude(), TWITTER_SEARCH_RADIUS_MILES, Geocode.Distance.MILES);
            Log.d(TAG, "searchOnTwitter: " + query + ", " + mLastLocation.getLatitude() + ", " + mLastLocation.getLongitude());
        }

        mProgressBar.setVisibility(View.VISIBLE);
        mNoResults.setVisibility(View.GONE);
        mFailedToGetTweets.setVisibility(View.GONE);

        // Perform twitter search
        TwitterApiClient twitterApiClient = TwitterCore.getInstance().getApiClient();
        SearchService searchService = twitterApiClient.getSearchService();
        searchService.tweets(query, geocode, null, null, "recent", MAX_NUMBER_OF_TWEETS,
                null, null, null, true,
                new Callback<Search>() {
                    @Override
                    public void success(Result<Search> searchResult) {
                        Search results = searchResult.data;
                        mTweets = results.tweets;
                        Log.d(TAG, "tweets -> success: " + mTweets.size());
                        mAdapter.setTweets(mTweets);
                        mNoResults.setVisibility(mTweets.size() == 0 ? View.VISIBLE : View.GONE);
                        mProgressBar.setVisibility(View.GONE);
                    }

                    @Override
                    public void failure(TwitterException exception) {
                        mProgressBar.setVisibility(View.GONE);

                        final TwitterApiException apiException = (TwitterApiException) exception;
                        final int errorCode = apiException.getErrorCode();
                        if (errorCode == TwitterApiConstants.Errors.APP_AUTH_ERROR_CODE || errorCode == TwitterApiConstants.Errors.GUEST_AUTH_ERROR_CODE) {
                            Log.d(TAG, "tweets -> failure: Twitter guest authentication expired. Logging in again...");
                            logInGuestToTwitter();
                        } else {
                            Log.w(TAG, "tweets -> failure: " + exception.getMessage());
                            mFailedToGetTweets.setVisibility(View.VISIBLE);
                        }
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
            Log.d(TAG, "onConnected: " + mLastLocation.getLatitude() + ", " + mLastLocation.getLongitude());
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
