package com.vn.qrscanner.features.scanner

import android.os.Parcel
import android.os.Parcelable

data class ScanResultItem(
    val result: String,
    val previewPath: String? = null
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(result)
        parcel.writeString(previewPath)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ScanResultItem> {
        override fun createFromParcel(parcel: Parcel): ScanResultItem {
            return ScanResultItem(parcel)
        }

        override fun newArray(size: Int): Array<ScanResultItem?> {
            return arrayOfNulls(size)
        }
    }
}
