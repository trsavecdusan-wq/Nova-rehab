<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:background="#1A1A2E">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="64dp"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:background="#0D0D1A"
        android:paddingStart="12dp"
        android:paddingEnd="12dp">

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="KONTAKTI ZA KLIC"
            android:textColor="#E94560"
            android:textSize="22sp"
            android:textStyle="bold" />

        <Button
            android:id="@+id/btnCommBack"
            android:layout_width="140dp"
            android:layout_height="48dp"
            android:text="NAZAJ"
            android:textSize="16sp"
            android:textStyle="bold"
            android:backgroundTint="#333355"
            android:textColor="#FFFFFF" />

    </LinearLayout>

    <TextView
        android:layout_width="match_parent"
        android:layout_height="56dp"
        android:gravity="center"
        android:text="Izberi osebo za video klic"
        android:textColor="#FFFFFF"
        android:textSize="22sp"
        android:textStyle="bold"
        android:background="#16213E" />

    <GridLayout
        android:id="@+id/gridContacts"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:columnCount="2"
        android:rowCount="3"
        android:padding="8dp" />

</LinearLayout>
