package com.getadhell.androidapp.viewmodel;


import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.util.Log;

import com.getadhell.androidapp.db.AppDatabase;
import com.getadhell.androidapp.db.entity.AppInfo;
import com.getadhell.androidapp.utils.DeviceUtils;
import com.sec.enterprise.AppIdentity;
import com.sec.enterprise.firewall.DomainFilterRule;
import com.sec.enterprise.firewall.Firewall;
import com.sec.enterprise.firewall.FirewallResponse;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Maybe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class AdhellWhitelistAppsViewModel extends AndroidViewModel {
    private static final String TAG = AdhellWhitelistAppsViewModel.class.getCanonicalName();
    private AppDatabase mAppDatabase;
    private LiveData<List<AppInfo>> appInfos;

    public AdhellWhitelistAppsViewModel(Application application) {
        super(application);
        mAppDatabase = AppDatabase.getAppDatabase(application);
    }

    public LiveData<List<AppInfo>> getSortedAppInfo() {
        if (appInfos == null) {
            appInfos = new MutableLiveData<>();
            loadAppInfos();
        }
        return appInfos;
    }

    private void loadAppInfos() {
        appInfos = mAppDatabase.applicationInfoDao().getAllSortedByWhitelist();
    }

    public void toggleApp(AppInfo appInfo) {
        Maybe.fromCallable(() -> {
            appInfo.adhellWhitelisted = !appInfo.adhellWhitelisted;
            mAppDatabase.applicationInfoDao().update(appInfo);


            List<DomainFilterRule> rules = new ArrayList<>();
            List<String> superAllow = new ArrayList<>();
            superAllow.add("*");
            rules.add(new DomainFilterRule(new AppIdentity(appInfo.packageName, null), new ArrayList<>(), superAllow));
            try {
                Firewall firewall = DeviceUtils.getEnterpriseDeviceManager().getFirewall();
                if (!firewall.isFirewallEnabled()) {
                    return null;
                }
                FirewallResponse[] response = null;
                // TODO: Check if firewall exists
                if (appInfo.adhellWhitelisted) {
                    // add allow rule
                    response = firewall.addDomainFilterRules(rules);
//                    response[0].getResult()
                } else {
                    // remove allow rule
                    response = firewall.removeDomainFilterRules(rules);
                }
                if (response[0].getResult() == FirewallResponse.Result.SUCCESS) {
                    Log.d(TAG, "Firewall app rules whitelist updated successfully");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to remove filter rule");
            }
            return null;
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe();
    }
}
