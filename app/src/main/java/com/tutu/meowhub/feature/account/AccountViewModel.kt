package com.tutu.meowhub.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tutu.meowhub.MeowApp
import com.tutu.meowhub.core.auth.MeowAppAuthManager
import com.tutu.meowhub.core.auth.MeowAppKeyData
import com.tutu.meowhub.core.auth.MeowAppOrder
import com.tutu.meowhub.core.auth.MeowAppUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountViewModel : ViewModel() {

    private val auth: MeowAppAuthManager = MeowApp.instance.meowAppAuth

    val isLoggedIn: StateFlow<Boolean> = auth.isLoggedIn
        .stateIn(viewModelScope, SharingStarted.Eagerly, auth.isLoggedIn.value)

    val currentUser: StateFlow<MeowAppUser?> = auth.currentUser
        .stateIn(viewModelScope, SharingStarted.Eagerly, auth.currentUser.value)

    val appKeyData: StateFlow<MeowAppKeyData?> = auth.appKeyData
        .stateIn(viewModelScope, SharingStarted.Eagerly, auth.appKeyData.value)

    // Login flow
    data class LoginState(
        val isLoading: Boolean = false,
        val codeSent: Boolean = false,
        val cooldownSeconds: Int = 0,
        val errorMessage: String? = null,
        val loginSuccess: Boolean = false
    )

    private val _loginState = MutableStateFlow(LoginState())
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    // Account detail
    data class AccountDetailState(
        val isLoading: Boolean = false,
        val credits: Int = 0,
        val rechargeUrl: String? = null,
        val billingInfo: String = "",
        val orders: List<MeowAppOrder> = emptyList(),
        val ordersPage: Int = 1,
        val hasMoreOrders: Boolean = false,
        val loadingOrders: Boolean = false,
        val errorMessage: String? = null
    )

    private val _accountState = MutableStateFlow(AccountDetailState())
    val accountState: StateFlow<AccountDetailState> = _accountState.asStateFlow()

    fun sendCode(target: String) {
        viewModelScope.launch {
            _loginState.value = _loginState.value.copy(isLoading = true, errorMessage = null)
            val result = auth.sendCode(target)
            result.fold(
                onSuccess = {
                    _loginState.value = _loginState.value.copy(
                        isLoading = false,
                        codeSent = true,
                        cooldownSeconds = 60
                    )
                    startCooldown()
                },
                onFailure = {
                    _loginState.value = _loginState.value.copy(
                        isLoading = false,
                        errorMessage = it.message ?: "发送失败"
                    )
                }
            )
        }
    }

    fun login(target: String, code: String) {
        viewModelScope.launch {
            _loginState.value = _loginState.value.copy(isLoading = true, errorMessage = null)
            val result = auth.login(target, code)
            result.fold(
                onSuccess = {
                    _loginState.value = _loginState.value.copy(
                        isLoading = false,
                        loginSuccess = true
                    )
                    auth.activateAppKey()
                    MeowApp.instance.connectWithAuth(force = true)
                },
                onFailure = {
                    _loginState.value = _loginState.value.copy(
                        isLoading = false,
                        errorMessage = it.message ?: "登录失败"
                    )
                }
            )
        }
    }

    fun resetLoginState() {
        _loginState.value = LoginState()
    }

    fun clearLoginError() {
        _loginState.value = _loginState.value.copy(errorMessage = null)
    }

    fun loadAccountDetail() {
        viewModelScope.launch {
            _accountState.value = _accountState.value.copy(isLoading = true, errorMessage = null)
            val profileResult = auth.fetchProfile()
            profileResult.fold(
                onSuccess = { profile ->
                    _accountState.value = _accountState.value.copy(
                        credits = profile.user?.credits ?: 0,
                        rechargeUrl = profile.rechargeUrl,
                        billingInfo = profile.billing?.let {
                            "每 ${it.creditsPer1kTokens} 积分 / 1000 tokens"
                        } ?: ""
                    )
                },
                onFailure = {
                    _accountState.value = _accountState.value.copy(
                        errorMessage = it.message
                    )
                }
            )
            loadOrders(page = 1, reset = true)
            _accountState.value = _accountState.value.copy(isLoading = false)
        }
    }

    fun loadOrders(page: Int = 1, reset: Boolean = false) {
        viewModelScope.launch {
            _accountState.value = _accountState.value.copy(loadingOrders = true)
            val result = auth.fetchOrders(page)
            result.fold(
                onSuccess = { resp ->
                    val currentOrders = if (reset) emptyList() else _accountState.value.orders
                    _accountState.value = _accountState.value.copy(
                        orders = currentOrders + resp.orders,
                        ordersPage = resp.page,
                        hasMoreOrders = resp.orders.size >= resp.pageSize,
                        loadingOrders = false,
                        rechargeUrl = resp.rechargeUrl ?: _accountState.value.rechargeUrl
                    )
                },
                onFailure = {
                    _accountState.value = _accountState.value.copy(loadingOrders = false)
                }
            )
        }
    }

    fun loadMoreOrders() {
        val current = _accountState.value
        if (!current.loadingOrders && current.hasMoreOrders) {
            loadOrders(page = current.ordersPage + 1)
        }
    }

    fun refreshCredits() {
        viewModelScope.launch {
            auth.fetchCredits().onSuccess {
                _accountState.value = _accountState.value.copy(credits = it.credits)
            }
        }
    }

    fun logout() {
        auth.logout()
        _accountState.value = AccountDetailState()
        MeowApp.instance.connectWithAuth(force = true)
    }

    private fun startCooldown() {
        viewModelScope.launch {
            var remaining = 60
            while (remaining > 0) {
                _loginState.value = _loginState.value.copy(cooldownSeconds = remaining)
                kotlinx.coroutines.delay(1000)
                remaining--
            }
            _loginState.value = _loginState.value.copy(cooldownSeconds = 0)
        }
    }
}
