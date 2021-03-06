package io.poundcode.gitdo.repositories.list;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

import io.poundcode.androidgithubapiwrapper.repository.GitHubRepository;
import io.poundcode.androidgithubapiwrapper.user.GitHubUser;
import io.poundcode.gitdo.Constants;
import io.poundcode.gitdo.R;
import io.poundcode.gitdo.data.analytics.AnalyticsIntentService;
import io.poundcode.gitdo.data.analytics.TrackedScreenView;
import io.poundcode.gitdo.data.repositories.RepositoryContract;
import io.poundcode.gitdo.data.sync.RepositorySyncAdapter;
import io.poundcode.gitdo.repositories.search.AddRepositoryActivity;
import io.poundcode.gitdo.repositorydetails.RepositoryDetailsActivity;
import io.poundcode.gitdo.utils.Extras;

public class RepositoriesActivity extends AppCompatActivity implements TrackedScreenView, LoaderManager.LoaderCallbacks<Cursor> {

    private static final int SEARCH_CODE = 233;
    @Bind(R.id.toolbar)
    Toolbar toolbar;
    @Bind(R.id.repositories)
    RecyclerView repositories;
    @Bind(R.id.fab)
    FloatingActionButton fab;
    private static final String TAG = "RepositoriesActivity";
    private RepositoriesAdapter mAdapter;
    private static final int LOADER_ID = 1;
    private List<GitHubRepository> mRepositoryList = new ArrayList<>();
    private BroadcastReceiver syncFinishedReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            //notify fragments to update
            Log.d(TAG, "onReceive: sync triggered");
            getSupportLoaderManager().restartLoader(LOADER_ID, null, RepositoriesActivity.this);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fireAnalytics();
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        setSupportActionBar(toolbar);
        RecyclerView.LayoutManager manager = new LinearLayoutManager(this);
        mAdapter = new RepositoriesAdapter();
        repositories.setLayoutManager(manager);
        repositories.setAdapter(mAdapter);
        getSupportLoaderManager().initLoader(LOADER_ID, null, this);
        RepositorySyncAdapter.initializeSyncAdapter(this);
//        RepositorySyncAdapter.syncImmediately(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(syncFinishedReceiver, new IntentFilter(Constants.SYNC_FINISHED));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (syncFinishedReceiver != null) {
            unregisterReceiver(syncFinishedReceiver);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            //repos were added reload data
            ArrayList<GitHubRepository> repositoryList = (ArrayList<GitHubRepository>) data.getSerializableExtra(Extras.REPOSITORIES);
            for (GitHubRepository repository : repositoryList) {
                ContentValues value = RepositoryContract.getContentValueForRepository(repository);
                getContentResolver().insert(RepositoryContract.BASE_URI, value);
            }
            getSupportLoaderManager().restartLoader(LOADER_ID, null, this);
        }
    }

    @OnClick(R.id.fab)
    public void onClickFab(View view) {
        Intent intent = new Intent(this, AddRepositoryActivity.class);
        startActivityForResult(intent, SEARCH_CODE);
    }

    public void fireAnalytics() {
        startService(AnalyticsIntentService.getScreenViewIntent(this, getScreenName()));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        return super.onOptionsItemSelected(item);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Uri content = RepositoryContract.BASE_URI;
        return new CursorLoader(this, content, null, null, null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        if (cursor == null) {
            Log.e(TAG, "onLoadFinished: cursor was null");
            return;
        }
        cursor.moveToFirst();
        List<GitHubRepository> repositoryList = new ArrayList<>(cursor.getCount());
        while (!cursor.isAfterLast()) {
            GitHubRepository repostiroy = new GitHubRepository();
            repostiroy.setId(cursor.getString(RepositoryContract.COL_REPO_ID));
            repostiroy.setName(cursor.getString(RepositoryContract.COL_NAME));
            repostiroy.setDescription(cursor.getString(RepositoryContract.COL_DESCRIPTION));
            repostiroy.setOpenIssuesCount(cursor.getInt(RepositoryContract.COL_ISSUE_COUNT));
            GitHubUser gitHubUser = new GitHubUser(cursor.getString(RepositoryContract.COL_USER));
            repostiroy.setOwner(gitHubUser);
            repositoryList.add(repostiroy);
            cursor.moveToNext();
        }

        mAdapter.setRepositoryList(repositoryList);

    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {

    }

    @Override
    public String getScreenName() {
        return getString(R.string.repository_list_screen);
    }

    public class RepositoriesAdapter extends RecyclerView.Adapter<RepositoryViewHolder> {

        @Override
        public RepositoryViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_repo, parent, false);
            return new RepositoryViewHolder(view);
        }

        @Override
        public void onBindViewHolder(RepositoryViewHolder holder, int position) {
            GitHubRepository repository = mRepositoryList.get(position);
            holder.tvRepoName.setText(repository.getName());
            if (TextUtils.isEmpty(repository.getDescription())) {
                holder.tvRepoDescription.setVisibility(View.GONE);
            }
            holder.tvRepoDescription.setText(repository.getDescription());
            // TODO: 5/18/2016 replace with R.string
            holder.tvOpenIssues.setText(getString(R.string.issue_count) + String.valueOf(repository.getOpenIssuesCount()));
            holder.tvUser.setText(getString(R.string.user) + " " + repository.getOwner().getLogin());
        }

        @Override
        public int getItemCount() {
            // TODO: 5/17/2016 replace with real data
            return mRepositoryList.size();
        }

        public void setRepositoryList(List<GitHubRepository> repositoryList) {
            mRepositoryList = repositoryList;
            notifyDataSetChanged();
        }
    }

    public class RepositoryViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        @Bind(R.id.tvRepoName)
        TextView tvRepoName;
        @Bind(R.id.tvRepoDescription)
        TextView tvRepoDescription;
        @Bind(R.id.tvOpenPullRequests)
        TextView tvOpenPullRequests;
        @Bind(R.id.tvOpenIssues)
        TextView tvOpenIssues;
        @Bind(R.id.user)
        TextView tvUser;

        public RepositoryViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            RepositoryDetailsActivity.startRepositoryDetailsActivityIntent(
                RepositoriesActivity.this, mRepositoryList.get(getAdapterPosition()));
        }
    }
}
