package com.closetvr.app.ui.screens.appointments

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.closetvr.app.data.model.AppointmentRequest
import com.closetvr.app.data.network.ApiService
import kotlinx.coroutines.launch

class AppointmentViewModel(
    private val apiService: ApiService,
    private val token: String,
    private val userId: Int
) : ViewModel() {

    var serviceName by mutableStateOf("Asesoría de Imagen Personalizada")
    var appointmentDate by mutableStateOf("2026-11-10 10:00:00")
    var statusMessage by mutableStateOf("")
    var isLoading by mutableStateOf(false)

    fun createAppointment(onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading = true
            statusMessage = "Agendando cita..."
            try {
                val request = AppointmentRequest(
                    userId = userId,
                    serviceName = serviceName,
                    appointmentDate = appointmentDate
                )

                val response = apiService.createAppointment("Bearer $token", request)

                if (response.isSuccessful && response.body()?.success == true) {
                    statusMessage = "¡Cita agendada con éxito!"
                    onSuccess()
                } else {
                    statusMessage = "Error al agendar: ${response.message()}"
                }
            } catch (e: Exception) {
                statusMessage = "Error de conexión: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }
}