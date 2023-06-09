package tech.android.jobsharing.fragment;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.nostra13.universalimageloader.core.ImageLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import tech.android.jobsharing.R;
import tech.android.jobsharing.activities.ConversationsActivity;
import tech.android.jobsharing.activities.NewPostActivity;
import tech.android.jobsharing.activities.SignInActivity;
import tech.android.jobsharing.adapter.NewFeedAdapter;
import tech.android.jobsharing.models.Comments;
import tech.android.jobsharing.models.Post;
import tech.android.jobsharing.utils.UniversalImageLoader;


/***
 * Created by HoangRyan aka LilDua on 11/6/2022.
 */
public class Fragment_Newsfeed extends Fragment implements SwipeRefreshLayout.OnRefreshListener{

    private View view;
    private LinearLayout empty;
    private ProgressBar progress;
    private AppCompatImageView imageViewSignOut,imageViewChat;
    private RecyclerView recyclerView;
    private ArrayList<Post> mPaginatedPhotos;
    private ArrayList<Post> mPost;
    private ArrayList<String> mFollowing;
    private int mResults;
    private String TAG = "Fragment_Home";
    private NewFeedAdapter mAdapter;
    private FloatingActionButton fab;
    int LAUNCH_SECOND_ACTIVITY = 1;
    private SwipeRefreshLayout swipeRefreshLayout;

    FirebaseAuth mAuth;
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.fragment_home, container, false);
        init();
        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        //set event listeners
        setListeners();
        mPost = new ArrayList<>();
        mPaginatedPhotos = new ArrayList<>();
        mFollowing = new ArrayList<>();
        return view;

    }
    private void init(){
        imageViewSignOut = view.findViewById(R.id.imageSignOut);
        imageViewChat = view.findViewById(R.id.imageChat);
        recyclerView = view.findViewById(R.id.fmNewFeed_rcv);
        progress = view.findViewById(R.id.fmHome_pbLoading);
        empty = view.findViewById(R.id.fmHome_llEmpty);
        fab = view.findViewById(R.id.fmHome_fab);
        swipeRefreshLayout = view.findViewById(R.id.swipeRefreshLayout);
        initImageLoader();
        getFollowing();
        displayMorePhotos();
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(getActivity(), NewPostActivity.class), LAUNCH_SECOND_ACTIVITY);
            }
        });
        swipeRefreshLayout.setOnRefreshListener(this);
        swipeRefreshLayout.setColorSchemeResources(R.color.primary);
    }
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == LAUNCH_SECOND_ACTIVITY) {
            if(resultCode == Activity.RESULT_OK){
                getFollowing();
            }
            if (resultCode == Activity.RESULT_CANCELED) {
            }
        }
    }

    private void initImageLoader() {
        UniversalImageLoader universalImageLoader = new UniversalImageLoader(getActivity());
        ImageLoader.getInstance().init(universalImageLoader.getConfig());
    }
    private void setListeners() {
        imageViewSignOut.setOnClickListener(v -> signOut());
        imageViewChat.setOnClickListener(v -> startActivity(new Intent(getContext(), ConversationsActivity.class)));
    }
    private void showToast(String message){
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }
    //sign out
    private void signOut(){
        mAuth.signOut();
        startActivity(new Intent(getContext(), SignInActivity.class));
    }
    public void displayMorePhotos(){
        Log.d(TAG, "displayMorePhotos: displaying more photos");
        try{
            if(mPost.size() > mResults && mPost.size() > 0){
                int iterations;
                if(mPost.size() > (mResults + 10)){
                    Log.d(TAG, "displayMorePhotos: there are greater than 10 more photos");
                    iterations = 10;
                }else{
                    Log.d(TAG, "displayMorePhotos: there is less than 10 more photos");
                    iterations = mPost.size() - mResults;
                }

                //add the new photos to the paginated results
                for(int i = mResults; i < mResults + iterations; i++){
                    mPaginatedPhotos.add(mPost.get(i));
                }
                mResults = mResults + iterations;
                mAdapter.notifyDataSetChanged();
            }
        }catch (NullPointerException e){
            Log.e(TAG, "displayPhotos: NullPointerException: " + e.getMessage() );
        }catch (IndexOutOfBoundsException e){
            Log.e(TAG, "displayPhotos: IndexOutOfBoundsException: " + e.getMessage() );
        }
    }
    private void getFollowing(){
        mPost = new ArrayList<>();
        mPaginatedPhotos = new ArrayList<>();
        mFollowing = new ArrayList<>();
        Log.d(TAG, "getFollowing: searching for following");
        progress.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        empty.setVisibility(View.GONE);
        DatabaseReference reference = FirebaseDatabase.getInstance().getReference();
        Query query = reference
                .child("Post");
        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                for(DataSnapshot singleSnapshot : dataSnapshot.getChildren()){
                    DataSnapshot dataSnapshot1 = singleSnapshot.getChildren().iterator().next();
                    mFollowing.add(dataSnapshot1.child("userId").getValue().toString());
                }
                getPostList();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    private void getPostList(){
        Log.d(TAG, "getPhotos: getting photos");
        DatabaseReference reference = FirebaseDatabase.getInstance().getReference();
        for(int i = 0; i < mFollowing.size(); i++){
            final int count = i;
            Query query = reference
                    .child("Post")
                    .child(mFollowing.get(i))
                    .orderByChild("userId")
                    .equalTo(mFollowing.get(i));
            query.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if (dataSnapshot.getChildrenCount() == 0) {
                        recyclerView.setVisibility(View.GONE);
                        progress.setVisibility(View.GONE);
                        empty.setVisibility(View.VISIBLE);
                        return;
                    }
                    for(DataSnapshot singleSnapshot : dataSnapshot.getChildren()){
                        Post photo = new Post();
                        Map<String, Object> objectMap = (HashMap<String, Object>) singleSnapshot.getValue();
                        photo.setCaption(objectMap.get("caption").toString());
                        photo.setTags(objectMap.get("tags").toString());
                        if (objectMap.get("post_id") != null)
                            photo.setPhoto_id(objectMap.get("post_id").toString());
                        if (objectMap.get("userId") != null)
                            photo.setUser_id(objectMap.get("userId").toString());
                        photo.setDate_Created(objectMap.get("date_Created").toString());
                        if (objectMap.get("image_Path") != null)
                            photo.setImage_Path(objectMap.get("image_Path").toString());
                        ArrayList<Comments> comments = new ArrayList<Comments>();
                        for (DataSnapshot dSnapshot : singleSnapshot
                                .child("comments").getChildren()){
                            Comments comment = new Comments();
                            comment.setUser_id(dSnapshot.getValue(Comments.class).getUser_id());
                            comment.setComment(dSnapshot.getValue(Comments.class).getComment());
                            comment.setDate_created(dSnapshot.getValue(Comments.class).getDate_created());
                            comments.add(comment);
                        }
                        photo.setComments(comments);
                        mPost.add(photo);
                    }
                    if(count >= mFollowing.size() -1){
                        //display our photos
                        displayPhotos();
                    }
                }

                @Override
                public void onCancelled(DatabaseError databaseError) {

                }
            });
        }
    }
    private void displayPhotos(){
        if(mPost != null){
            try{
                Collections.sort(mPost, new Comparator<Post>() {
                    @Override
                    public int compare(Post o1, Post o2) {
                        return o2.getDate_Created().compareTo(o1.getDate_Created());
                    }
                });
                int iterations = mPost.size();

                if(iterations > 10){
                    iterations = 10;
                }
                mResults = 10;
                for(int i = 0; i < iterations; i++){
                    mPaginatedPhotos.add(mPost.get(i));
                }
                mAdapter = new NewFeedAdapter(getActivity(), mPaginatedPhotos);
                recyclerView.setVisibility(View.VISIBLE);
                progress.setVisibility(View.GONE);
                empty.setVisibility(View.GONE);
                recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
                recyclerView.setAdapter(mAdapter);
                mAdapter.notifyDataSetChanged();
            }catch (NullPointerException e){
                Log.e(TAG, "displayPhotos: NullPointerException: " + e.getMessage() );
            }catch (IndexOutOfBoundsException e){
                Log.e(TAG, "displayPhotos: IndexOutOfBoundsException: " + e.getMessage() );
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onRefresh() {
        getFollowing();
        Handler handler = new Handler();
        handler.postDelayed(() -> {
            swipeRefreshLayout.setRefreshing(false);
        },1000);
    }
}
