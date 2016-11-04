package com.lh.imbilibili.view.partion;

import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.GridLayoutManager;
import android.view.View;

import com.lh.imbilibili.R;
import com.lh.imbilibili.cache.CacheTransformer;
import com.lh.imbilibili.data.ApiException;
import com.lh.imbilibili.data.RetrofitHelper;
import com.lh.imbilibili.model.BilibiliDataResponse;
import com.lh.imbilibili.model.partion.PartionHome;
import com.lh.imbilibili.model.partion.PartionVideo;
import com.lh.imbilibili.utils.RxBus;
import com.lh.imbilibili.utils.SubscriptionUtils;
import com.lh.imbilibili.utils.ToastUtils;
import com.lh.imbilibili.view.LazyLoadFragment;
import com.lh.imbilibili.view.adapter.partion.PartionHomeRecyclerViewAdapter;
import com.lh.imbilibili.view.adapter.partion.PartionItemDecoration;
import com.lh.imbilibili.view.adapter.partion.model.PartionModel;
import com.lh.imbilibili.view.common.WebViewActivity;
import com.lh.imbilibili.view.video.VideoDetailActivity;
import com.lh.imbilibili.widget.LoadMoreRecyclerView;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import butterknife.BindView;
import butterknife.ButterKnife;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

/**
 * Created by liuhui on 2016/9/29.
 * 分区主页
 */

public class PartionHomeFragment extends LazyLoadFragment implements LoadMoreRecyclerView.OnLoadMoreLinstener, SwipeRefreshLayout.OnRefreshListener, PartionHomeRecyclerViewAdapter.OnItemClickListener {
    private static final String EXTRA_DATA = "partionModel";
    private static final int PAGE_SIZE = 50;

    @BindView(R.id.swiperefresh_layout)
    SwipeRefreshLayout mSwipeRefreshLayout;
    @BindView(R.id.recycler_view)
    LoadMoreRecyclerView mRecyclerView;

    private PartionHome mPartionHomeData;
    private List<PartionVideo> mPartionVideos;

    private PartionHomeRecyclerViewAdapter mAdapter;
    private PartionModel mPartionModel;
    private Subscription mPartionAllDataSub;
    private Subscription mPartionLoadMoreSub;

    private int mCurrentPage = 1;
    private boolean mNeedForeRefresh;

    public static PartionHomeFragment newInstance(PartionModel partionModel) {
        PartionHomeFragment fragment = new PartionHomeFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(EXTRA_DATA, partionModel);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    protected void initView(View view) {
        mPartionModel = getArguments().getParcelable(EXTRA_DATA);
        ButterKnife.bind(this, view);
        mCurrentPage = 1;
        mNeedForeRefresh = false;
        initRecyclerView();
        mSwipeRefreshLayout.setOnRefreshListener(this);
    }

    @Override
    protected int getContentView() {
        return R.layout.fragment_partion_home;
    }

    private void loadAllData() {
        mPartionAllDataSub = Observable.mergeDelayError(RetrofitHelper.getInstance()
                .getPartionService()
                .getPartionInfo(mPartionModel.getId(), "*")
                .compose(new CacheTransformer<BilibiliDataResponse<PartionHome>>("partion_home_" + mPartionModel.getId(), mNeedForeRefresh) {
                })
                .flatMap(new Func1<BilibiliDataResponse<PartionHome>, Observable<PartionHome>>() {
                    @Override
                    public Observable<PartionHome> call(BilibiliDataResponse<PartionHome> partionHomeBilibiliDataResponse) {
                        if (partionHomeBilibiliDataResponse.isSuccess()) {
                            mPartionHomeData = partionHomeBilibiliDataResponse.getData();
                            return Observable.just(partionHomeBilibiliDataResponse.getData());
                        } else {
                            return Observable.error(new ApiException(partionHomeBilibiliDataResponse.getCode()));
                        }
                    }
                }), loadDynamicData())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<Object>() {
                    @Override
                    public void onCompleted() {
                        finishTask();
                    }

                    @Override
                    public void onError(Throwable e) {
                        finishTask();
                        mRecyclerView.setLodingViewState(LoadMoreRecyclerView.STATE_FAIL);
                        mRecyclerView.setEnableLoadMore(false);
                        ToastUtils.showToastShort(R.string.load_error);
                    }

                    @Override
                    public void onNext(Object o) {

                    }
                });

    }

    private void finishTask() {
        mSwipeRefreshLayout.setRefreshing(false);
        if (mPartionHomeData != null) {
            mAdapter.setPartionData(mPartionHomeData);
        }
        if (mPartionVideos != null) {
            mAdapter.clearDynamicVideo();
            mAdapter.addDynamicVideo(mPartionVideos);
            mCurrentPage++;
        }
        if (mPartionHomeData != null || mPartionVideos != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    private Observable<List<PartionVideo>> loadDynamicData() {
        return RetrofitHelper.getInstance()
                .getPartionService()
                .getPartionDynamic(mPartionModel.getId(), mCurrentPage, PAGE_SIZE)
                .compose(new CacheTransformer<BilibiliDataResponse<List<PartionVideo>>>("partion_home_dynamic" + mPartionModel.getId(), mNeedForeRefresh) {
                    @Override
                    protected boolean canCache() {
                        return mCurrentPage == 1;
                    }
                })
                .flatMap(new Func1<BilibiliDataResponse<List<PartionVideo>>, Observable<List<PartionVideo>>>() {
                    @Override
                    public Observable<List<PartionVideo>> call(BilibiliDataResponse<List<PartionVideo>> listBilibiliDataResponse) {
                        if (listBilibiliDataResponse.isSuccess()) {
                            mPartionVideos = listBilibiliDataResponse.getData();
                            return Observable.just(listBilibiliDataResponse.getData());
                        } else {
                            return Observable.error(new ApiException(listBilibiliDataResponse.getCode()));
                        }
                    }
                });
    }

    @Override
    protected void fetchData() {
        loadAllData();
    }

    private void initRecyclerView() {
        GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), 2);
        gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                switch (mRecyclerView.getItemViewType(position)) {
                    case PartionHomeRecyclerViewAdapter.TYPE_BANNER:
                    case PartionHomeRecyclerViewAdapter.TYPE_HOT_RECOMMEND_HEAD:
                    case PartionHomeRecyclerViewAdapter.TYPE_NEW_VIDEO_HEAD:
                    case PartionHomeRecyclerViewAdapter.TYPE_PARTION_DYNAMIC_HEAD:
                    case PartionHomeRecyclerViewAdapter.TYPE_SUB_PARTION:
                    case LoadMoreRecyclerView.TYPE_LOAD_MORE:
                        return 2;
                    case PartionHomeRecyclerViewAdapter.TYPE_HOT_RECOMMEND_ITEM:
                    case PartionHomeRecyclerViewAdapter.TYPE_NEW_VIDEO_ITEM:
                    case PartionHomeRecyclerViewAdapter.TYPE_PARTION_DYNAMIC_ITME:
                        return 1;
                    default:
                        return 1;
                }
            }
        });
        PartionItemDecoration itemDecoration = new PartionItemDecoration(getContext());
        if (mAdapter == null) {
            mAdapter = new PartionHomeRecyclerViewAdapter(getContext(), mPartionModel);
        }
        mRecyclerView.setLayoutManager(gridLayoutManager);
        mRecyclerView.addItemDecoration(itemDecoration);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setEnableLoadMore(true);
        mRecyclerView.setOnLoadMoreLinstener(this);
        mAdapter.setOnItemClickListener(this);
    }

    @Override
    public void onLoadMore() {
        mPartionLoadMoreSub = loadDynamicData().subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<List<PartionVideo>>() {
                    @Override
                    public void call(List<PartionVideo> partionVideos) {
                        mRecyclerView.setLoading(false);
                        if (partionVideos.size() == 0) {
                            mRecyclerView.setLodingViewState(LoadMoreRecyclerView.STATE_NO_MORE);
                            mRecyclerView.setEnableLoadMore(false);
                        } else {
                            int startPosition = mAdapter.getItemCount();
                            mAdapter.addDynamicVideo(partionVideos);
                            mAdapter.notifyItemRangeInserted(startPosition, partionVideos.size());
                        }
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        mRecyclerView.setLoading(false);
                        mRecyclerView.setLodingViewState(LoadMoreRecyclerView.STATE_FAIL);
                        ToastUtils.showToastShort(R.string.load_error);
                    }
                });
    }

    @Override
    public void onRefresh() {
        mCurrentPage = 1;
        mRecyclerView.setEnableLoadMore(true);
        mRecyclerView.setLodingViewState(LoadMoreRecyclerView.STATE_REFRESHING);
        mNeedForeRefresh = true;
        loadAllData();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        SubscriptionUtils.unsubscribe(mPartionAllDataSub, mPartionLoadMoreSub);
    }

    @Override
    public void onBannerClick(String uri) {
        Pattern p = Pattern.compile("av(\\d+)");
        Matcher m = p.matcher(uri);
        if (m.find()) {
            VideoDetailActivity.startActivity(getContext(), m.group(1));
        } else {
            WebViewActivity.startActivity(getContext(), uri);
        }
    }

    @Override
    public void onVideoItemClick(String aid) {
        VideoDetailActivity.startActivity(getContext(), aid);
    }

    @Override
    public void onSubPartionItemClick(int position) {
        RxBus.getInstance().send(new SubPartionClickEvent(position));
    }

    @Override
    public void onHeadItemClick(int type) {
        if (type == PartionHomeRecyclerViewAdapter.TYPE_NEW_VIDEO_HEAD) {
            RxBus.getInstance().send(new SubPartionClickEvent(1));
        }
    }
}
