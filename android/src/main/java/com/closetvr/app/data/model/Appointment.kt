package com.closetvr.app.data.model

data class AppointmentRequest(
    val userId: Int,
    val serviceName: String,
    val appointmentDate: String
)

data class AppointmentResponse(
    val success: Boolean,
    val message: String,
    val appointment: AppointmentDto?
)

data class AppointmentDto(
    val id: Int,
    val user_id: Int,
    val service_name: String,
    val appointment_date: String,
    val status: String
)