package com.miwealth.sovereignvantage.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miwealth.sovereignvantage.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val savedEmail: String = "",
    val error: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    
    init {
        loadSavedEmail()
        checkExistingSession()
    }
    
    private fun loadSavedEmail() {
        viewModelScope.launch {
            val email = authRepository.getSavedEmail()
            if (email != null) {
                _uiState.update { it.copy(savedEmail = email) }
            }
        }
    }
    
    private fun checkExistingSession() {
        viewModelScope.launch {
            if (authRepository.hasValidSession()) {
                _uiState.update { it.copy(isLoggedIn = true) }
            }
        }
    }
    
    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                val result = authRepository.login(email, password)
                result.fold(
                    onSuccess = {
                        _uiState.update { it.copy(isLoading = false, isLoggedIn = true) }
                    },
                    onFailure = { exception ->
                        _uiState.update { 
                            it.copy(
                                isLoading = false, 
                                error = exception.message ?: "Login failed"
                            ) 
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        error = e.message ?: "An unexpected error occurred"
                    ) 
                }
            }
        }
    }
    
    fun biometricLogin() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                val result = authRepository.biometricLogin()
                result.fold(
                    onSuccess = {
                        _uiState.update { it.copy(isLoading = false, isLoggedIn = true) }
                    },
                    onFailure = { exception ->
                        _uiState.update { 
                            it.copy(
                                isLoading = false, 
                                error = exception.message ?: "Biometric authentication failed"
                            ) 
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        error = e.message ?: "An unexpected error occurred"
                    ) 
                }
            }
        }
    }
    
    fun setBiometricError(message: String) {
        _uiState.update { it.copy(isLoading = false, error = message) }
    }
}
