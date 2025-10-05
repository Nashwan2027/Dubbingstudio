/**
 * Copyright (c) 2024 Nashwan.
 * 
 * Licensed under the MIT License.
 * See the LICENSE file for details.
 */

package com.nash.dubbingstudio.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Dialogue(
    val id: Int,
    val startTime: String,
    val endTime: String,
    val text: String
) : Parcelable