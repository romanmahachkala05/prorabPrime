package ru.prorabprime.domain.model

import kotlin.jvm.JvmInline

@JvmInline
value class ObjectId(
    val value: String,
)

@JvmInline
value class PhotoId(
    val value: String,
)

@JvmInline
value class ContactId(
    val value: String,
)

@JvmInline
value class PaymentId(
    val value: String,
)

@JvmInline
value class ExtraWorkId(
    val value: String,
)

@JvmInline
value class MaterialId(
    val value: String,
)

@JvmInline
value class TaskId(
    val value: String,
)
