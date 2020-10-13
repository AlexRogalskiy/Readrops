package com.readrops.app

import com.readrops.app.repositories.FreshRSSRepository
import com.readrops.app.repositories.LocalFeedRepository
import com.readrops.app.repositories.NextNewsRepository
import com.readrops.app.viewmodels.*
import com.readrops.db.entities.account.Account
import com.readrops.db.entities.account.AccountType
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {

    factory { (account: Account) ->
        when (account.accountType) {
            AccountType.LOCAL -> LocalFeedRepository(get(), get(), androidContext(), account)
            AccountType.NEXTCLOUD_NEWS -> NextNewsRepository(get(), get(), androidContext(), account)
            AccountType.FRESHRSS -> FreshRSSRepository(get(), get(), androidContext(), account)
            else -> throw IllegalArgumentException("Account type not supported")
        }
    }

    viewModel {
        MainViewModel(androidApplication())
    }

    viewModel {
        AddFeedsViewModel(androidApplication())
    }

    viewModel {
        ItemViewModel(androidApplication())
    }

    viewModel {
        ManageFeedsFoldersViewModel(androidApplication())
    }

    viewModel {
        NotificationPermissionViewModel(androidApplication())
    }

}