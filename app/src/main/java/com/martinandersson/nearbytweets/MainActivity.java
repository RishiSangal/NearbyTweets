package com.martinandersson.nearbytweets;

import android.location.Location;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;
import io.fabric.sdk.android.Fabric;

public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    // Note: Your consumer key and secret should be obfuscated in your source code before shipping.
    private static final String TWITTER_KEY = "cwYlodNmgA5veS0EShHFl2yQm";
    private static final String TWITTER_SECRET = "JePHisPGIM3Lr2AM0lBznkWJduCFrvKItjZgtaI418ISMcNysP";

    public static final String TAG = MainActivity.class.getSimpleName();

    @InjectView(R.id.results_textview)
    TextView resultsTextview;

    @InjectView(R.id.textview)
    TextView mTextView;

    private GoogleApiClient mGoogleApiClient;
    private Location mLastLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TwitterAuthConfig authConfig = new TwitterAuthConfig(TWITTER_KEY, TWITTER_SECRET);
        Fabric.with(this, new Twitter(authConfig));
        setContentView(R.layout.activity_main);
        ButterKnife.inject(this);

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

    private void searchOnTwitter() {
        TwitterApiClient twitterApiClient = TwitterCore.getInstance().getApiClient();
        SearchService searchService = twitterApiClient.getSearchService();

        String query = null;
        Geocode geocode = new Geocode(mLastLocation.getLatitude(), mLastLocation.getLongitude(), 10, Geocode.Distance.MILES);

        try {
            query = URLEncoder.encode("Dallas", "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        Log.d(TAG, "searchOnTwitter: " + query);

        searchService.tweets(query, geocode, null, null, "recent", 20,
                null, null, null, true,
                new Callback<Search>() {
                    @Override
                    public void success(Result<Search> searchResult) {
                        Search results = searchResult.data;
                        List<Tweet> tweets = results.tweets; // tweets.size() = 9 (e.g.)

                        StringBuilder sb = new StringBuilder();
                        Log.d(TAG, "tweets -> success: " + tweets.size());
                        for (Tweet tweet : tweets) {
                            if (tweet != null) {

                                String tweetLocation = "unknown";
                                if (tweet.coordinates != null) {
                                    tweetLocation = tweet.coordinates.getLatitude() + "," + tweet.coordinates.getLongitude();
                                }
                                Log.d(TAG, tweetLocation + ": " + tweet.text);
                                sb.append(tweet.text).append('\n');
                            }
                        }
                        resultsTextview.setText(sb.toString());
                    }

                    @Override
                    public void failure(TwitterException e) {
                        // ignorelint.xml
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
            mTextView.setText(text);
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
