/*
 * This file is part of Butter.
 *
 * Butter is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Butter is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Butter. If not, see <http://www.gnu.org/licenses/>.
 */

package butter.droid.ui.main;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import butter.droid.MobileButterApplication;
import butter.droid.R;
import butter.droid.base.Constants;
import butter.droid.base.PlayerTestConstants;
import butter.droid.base.manager.internal.beaming.BeamPlayerNotificationService;
import butter.droid.base.manager.internal.beaming.server.BeamServerService;
import butter.droid.base.torrent.StreamInfo;
import butter.droid.provider.base.module.Movie;
import butter.droid.ui.ButterBaseActivity;
import butter.droid.ui.beam.BeamPlayerActivity;
import butter.droid.ui.loading.StreamLoadingActivity;
import butter.droid.ui.main.navigation.NavigationDrawerFragment;
import butter.droid.ui.main.pager.MediaPagerAdapter;
import butter.droid.ui.main.pager.NavInfo;
import butter.droid.ui.player.VideoPlayerActivity;
import butter.droid.ui.preferences.PreferencesActivity;
import butter.droid.ui.search.SearchActivity;
import butter.droid.ui.terms.TermsActivity;
import butter.droid.ui.trailer.TrailerPlayerActivity;
import butter.droid.utils.ToolbarUtils;
import butter.droid.widget.ScrimInsetsFrameLayout;
import butterknife.BindView;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.List;
import javax.inject.Inject;
import timber.log.Timber;

/**
 * The main activity that houses the navigation drawer, and controls navigation between fragments
 */
public class MainActivity extends ButterBaseActivity implements MainView {

    private static final String EXTRA_PROVIDER = "butter.droid.ui.main.MainActivity.providerId";

    private static final int REQUEST_CODE_TERMS = 1;
    private static final int PERMISSIONS_REQUEST_STORAGE = 1;

    @Inject MainPresenter presenter;

    @BindView(R.id.toolbar) Toolbar toolbar;
    @BindView(R.id.navigation_drawer_container) ScrimInsetsFrameLayout navigationDrawerContainer;
    @BindView(R.id.tabs) TabLayout tabs;
    @BindView(R.id.pager) ViewPager viewPager;

    private MainComponent component;
    private NavigationDrawerFragment navigationDrawerFragment;
    private ActionBarDrawerToggle drawerToggle;
    private DrawerLayout drawerLayout;
    private MediaPagerAdapter adapter;

    @SuppressLint("MissingSuperCall")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        component = MobileButterApplication.getAppContext()
                .getComponent()
                .mainComponentBuilder()
                .mainModule(new MainModule(this))
                .build();
        component.inject(this);

        super.onCreate(savedInstanceState, R.layout.activity_main);

        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeButtonEnabled(true);

        setShowCasting(true);
        ToolbarUtils.updateToolbarHeight(this, toolbar);
        setupDrawer();
        setupTabs();

        adapter = new MediaPagerAdapter(getSupportFragmentManager(), this);
        viewPager.setAdapter(adapter);

        int defaultProviderId = -1;
        if (savedInstanceState != null) {
            defaultProviderId = savedInstanceState.getInt(EXTRA_PROVIDER, defaultProviderId);
        }

        presenter.onCreate(defaultProviderId);
    }

    @Override
    protected void onResume() {
        super.onResume();

        presenter.onResume();

        supportInvalidateOptionsMenu();

        if (BeamServerService.getServer() != null) {
            BeamServerService.getServer().stop();
        }

        BeamPlayerNotificationService.cancelNotification();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.activity_overview, menu);

        MenuItem playerTestMenuItem = menu.findItem(R.id.action_playertests);
        playerTestMenuItem.setVisible(Constants.DEBUG_ENABLED);

        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_STORAGE:
                if (grantResults.length < 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    presenter.storagePermissionDenied();
                } else {
                    presenter.storagePermissionGranted();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (drawerToggle.onOptionsItemSelected(item)) {
            return true;
        } else {
            switch (item.getItemId()) {
                case android.R.id.home:
                /* Override default {@link pct.droid.activities.BaseActivity } behaviour */
                    return false;
                case R.id.action_playertests:
                    presenter.playerTestClicked();
                    return true;
                case R.id.action_search:
                    presenter.searchClicked();
                    return true;
                default:
                    return super.onOptionsItemSelected(item);
            }
        }
    }

    @Override public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        drawerToggle.onConfigurationChanged(newConfig);
    }

    @Override protected void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        presenter.onSaveInstanceState(outState);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CODE_TERMS:
                if (resultCode == RESULT_CANCELED) {
                    finish();
                }
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
                break;
        }
    }

    @Override public void showTermsScreen() {
        startActivityForResult(TermsActivity.getIntent(this), REQUEST_CODE_TERMS);
    }

    @Override public void showYoutubeVideo(Movie movie, String url) {
        Intent intent = TrailerPlayerActivity.getIntent(this, movie, url);
        startActivity(intent);
    }

    @Override public void showPlayerTestDialog(String[] fileTypes) {
        new AlertDialog.Builder(this)
                .setTitle("Player Tests")
                .setNegativeButton("Cancel", (dialogInterface, which) -> dialogInterface.dismiss())
                .setSingleChoiceItems(fileTypes, -1, (dialogInterface, index) -> {
                    dialogInterface.dismiss();
                    presenter.onPlayerTestItemClicked(index);
                })
                .show();
    }

    @Override public void showPlayerTestUrlDialog() {
        final EditText dialogInput = new EditText(MainActivity.this);
        dialogInput.setText(PlayerTestConstants.DEFAULT_CUSTOM_FILE);

        new AlertDialog.Builder(MainActivity.this)
                .setView(dialogInput)
                .setPositiveButton("Start", (dialog, which) -> {
                    String location = dialogInput.getText().toString();
                    presenter.openPlayerTestCustomUrl(location);
                })
                .show();
    }

    @Override public void showVideoPlayer(@NonNull StreamInfo info) {
        startActivity(VideoPlayerActivity.getIntent(this, info));
    }

    @Override public void showBeamPlayer(@NonNull StreamInfo info) {
        startActivity(BeamPlayerActivity.getIntent(this, info, 0));
    }

    @Override public void requestStoragePermissions() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                PERMISSIONS_REQUEST_STORAGE);
    }

    @Override public void closeScreen() {
        finish();
    }

    @Override public void checkIntentAction() {
        checkActions();
    }

    @Override public void initProviders(final int providerId) {
        navigationDrawerFragment.selectProvider(providerId);
    }

    @Override public void closeDrawer() {
        drawerLayout.closeDrawer(navigationDrawerContainer);
    }

    @Override public void openPreferenceScreen() {
        startActivity(PreferencesActivity.getIntent(this));
    }

    @Override public void displayProvider(@StringRes int title, List<NavInfo> navigation) {
        setTitle(title);
        adapter.setData(navigation);

        // TODO: 6/17/17
//        tabs.getTabAt(hasGenres ? 1 : 0).select();
    }

    @Override public void onGenreChanged(String genre) {
        adapter.setGenre(genre);
    }

    @Override public void showFirsContentScreen() {
        tabs.getTabAt(1).select();
    }

    @Override public void writeStateData(@NonNull final Bundle outState, final int providerId) {
        outState.putInt(EXTRA_PROVIDER, providerId);
    }

    @Override public void setScreenTitle(@StringRes final int title) {
        setTitle(title);
    }

    @Override public void openSearchScreen(final int providerId) {
        startActivity(SearchActivity.getIntent(this, providerId));
    }

    public MainComponent getComponent() {
        return component;
    }

    private void checkActions() {
        String action = getIntent().getAction();
        Uri data = getIntent().getData();
        if (action != null && action.equals(Intent.ACTION_VIEW) && data != null) {
            String streamUrl = data.toString();
            try {
                streamUrl = URLDecoder.decode(streamUrl, "UTF-8");
                StreamLoadingActivity.startActivity(this, new StreamInfo(streamUrl));
                finish();
            } catch (UnsupportedEncodingException e) {
                Timber.d("Unknown encoding"); // this should never happen
            }
        }
    }

    private void setupDrawer() {
        // Set up the drawer.
        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawerLayout.setStatusBarBackgroundColor(ContextCompat.getColor(this, R.color.primary_dark));

        navigationDrawerFragment =
                (NavigationDrawerFragment) getSupportFragmentManager().findFragmentById(
                        R.id.navigation_drawer_fragment);

        // ActionBarDrawerToggle ties together the the proper interactions
        // between the navigation drawer and the action bar app icon.
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.string.drawer_open, R.string.drawer_close);
        drawerToggle.syncState();
        drawerLayout.addDrawerListener(drawerToggle);

    }

    private void setupTabs() {

        tabs.setupWithViewPager(viewPager);
        tabs.setTabGravity(TabLayout.GRAVITY_CENTER);
        tabs.setTabMode(TabLayout.MODE_SCROLLABLE);
        tabs.setVisibility(View.VISIBLE);

        viewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabs));
        tabs.addOnTabSelectedListener(new TabLayout.ViewPagerOnTabSelectedListener(viewPager));

    }

}