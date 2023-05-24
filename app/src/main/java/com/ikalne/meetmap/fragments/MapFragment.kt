package com.ikalne.meetmap.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.ikalne.meetmap.R
import com.ikalne.meetmap.api.models.LocatorView
import com.ikalne.meetmap.model.CustomInfoWindowAdapter
import com.ikalne.meetmap.viewmodels.MadridViewModel
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.ikalne.meetmap.model.LocationMenuAdapter
import com.ikalne.meetmap.model.LocationMenuItem
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import com.google.maps.android.clustering.ClusterManager
import com.ikalne.meetmap.model.MyItem
import com.ikalne.meetmap.model.MyClusterRenderer

class MapFragment : Fragment(), GoogleMap.OnInfoWindowClickListener, OnMapReadyCallback,
    GoogleMap.OnMyLocationButtonClickListener, GoogleMap.OnMyLocationClickListener{

    private lateinit var map: GoogleMap
    private lateinit var loadingSpinner: ProgressBar
    private lateinit var dimView: View
    private val viewModel: MadridViewModel by lazy {ViewModelProvider(this)[MadridViewModel::class.java]}
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var clusterManager: ClusterManager<MyItem>
    private lateinit var chipGroup :ChipGroup
    private val infoFragment = InfoActivityFragment()

    companion object {
        const val REQUEST_CODE_LOCATION = 0
        val madridMap = hashMapOf<String, String>()
        var locatorList = listOf<LocatorView>()
        var locatorListFav = listOf<LocatorView>()
        val markers = mutableMapOf<String, Marker>()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_map, container, false).apply {
        chipGroup = findViewById(R.id.chip_group)
        dimView = findViewById(R.id.dim_view)
        dimView.setOnClickListener(null)
        dimView.visibility = View.VISIBLE
        loadingSpinner = findViewById(R.id.loading_spinner)
        loadingSpinner.visibility = View.VISIBLE
        observe()
        val mMapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mMapFragment.getMapAsync(this@MapFragment)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
    }

    @SuppressLint("PotentialBehaviorOverride")
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        enableLocation()
        viewModel.fetchData()
        map.setOnMarkerClickListener { false }
        map.setOnMyLocationClickListener(this)
        clusterManager = ClusterManager<MyItem>(requireContext(), map)
        val clusterRenderer = MyClusterRenderer(requireContext(), map, clusterManager,requireActivity().supportFragmentManager)
        clusterManager.renderer = clusterRenderer
        clusterManager.setOnClusterClickListener(clusterRenderer)
        map.setOnCameraIdleListener(clusterManager)
    }

    @SuppressLint("PotentialBehaviorOverride")
    private fun observe() {
        viewModel.locators.observe(viewLifecycleOwner) { locators ->
            clusterManager.clearItems()
            map.clear()
            val items = mutableListOf<MyItem>()
            items.clear()
            locators.mapNotNull {
                it.location.latitude?.let { lat ->
                    it.location.longitude?.let { lng ->
                        LatLng(lat, lng)
                    }
                }?.let { coordinates ->
                    val item = MyItem(coordinates, "${it.id} ${it.title}", "${it.time} ${it.dstart}")
                    items.add(item)
                    madridMap[item.getNombre()] = it.id
                    val markerOptions = MarkerOptions().position(coordinates).title("${it.id} ${it.title}").visible(false)
                    val marker = map.addMarker(markerOptions)
                    markers[marker.title] = marker
                    madridMap[marker.title] = it.id
                }
            }.also {
                locatorList = locators
                locatorListFav = locators
                map.setInfoWindowAdapter(
                    CustomInfoWindowAdapter(
                        LayoutInflater.from(activity),
                        locatorList
                    )
                )
                map.setOnInfoWindowClickListener(this@MapFragment)
                loadingSpinner.visibility = View.GONE
                dimView.visibility = View.GONE
                clusterManager.addItems(items)
                clusterManager.cluster()
                chipCreator(locators)
            }
        }
    }
    @SuppressLint("PotentialBehaviorOverride")
    private fun chipCreator(locators: List<LocatorView>) {
        val categories = mutableSetOf<String>()
        val selectedCategories = mutableSetOf<String>()
        for (locator in locators) {
            val category = locator.category.split("/").getOrNull(6)
            category?.let { categories.add(it) }
        }
        categories.sorted().forEach { category ->
            val chip = layoutInflater.inflate(R.layout.location_chip, chipGroup, false) as Chip
            chip.text = category
            chipGroup.addView(chip)
            chip.setOnClickListener {
                val isSelected = selectedCategories.contains(category)
                if (isSelected) {
                    selectedCategories.remove(category)
                    chip.setChipBackgroundColorResource(android.R.color.transparent)
                    chip.setTextColor(ContextCompat.getColorStateList(requireContext(), android.R.color.black))
                } else {
                    selectedCategories.add(category)
                    chip.setChipBackgroundColorResource(R.color.primary_light)
                    chip.setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.primary))
                }
                if (selectedCategories.isEmpty()) {
                    applyFilter(locators)
                } else {
                    val filteredLocators = locators.filter {
                        val cat = it.category.split("/").getOrNull(6)
                        selectedCategories.contains(cat)
                    }
                    applyFilter(filteredLocators)
                }
            }
        }
    }

    @SuppressLint("PotentialBehaviorOverride")
    private fun applyFilter(filteredLocators: List<LocatorView>) {
        map.clear()
        clusterManager.clearItems()
        val items = mutableListOf<MyItem>()
        filteredLocators.mapNotNull { locator ->
            locator.location.latitude?.let { lat ->
                locator.location.longitude?.let { lng ->
                    LatLng(lat, lng)
                }
            }?.let { coordinates ->
                val item = MyItem(coordinates, "${locator.id} ${locator.title}", "${locator.time} ${locator.dstart}")
                items.add(item)
            }
        }
        locatorList = filteredLocators
        map.setInfoWindowAdapter(
            CustomInfoWindowAdapter(
                LayoutInflater.from(activity),
                locatorList
            )
        )
        clusterManager.addItems(items)
        clusterManager.cluster()
    }

    private fun enableLocation() {
        if (!::map.isInitialized) return
        if (isLocationPermissionGranted()) {
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            map.isMyLocationEnabled = true
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                }
            }
        } else {
            requestLocationPermission()
        }
    }

    private fun isLocationPermissionGranted() = ContextCompat.checkSelfPermission(
        requireActivity(),
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    private fun requestLocationPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                requireActivity(),
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        ) {
            Toast.makeText(activity, R.string.location, Toast.LENGTH_SHORT).show()
        } else {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_CODE_LOCATION
            )
        }
    }

    override fun onMyLocationButtonClick() = false

    override fun onInfoWindowClick(marker: Marker) {
        infoFragment.setMarker(marker, locatorList)
        requireActivity().supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.frame, infoFragment)
            .addToBackStack(null)
            .commit()
    }


    @SuppressLint("InflateParams")
    override fun onMyLocationClick(location: Location) {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.location_menu, null)
        bottomSheetDialog.setContentView(view)
        val menuItems = mutableListOf<LocationMenuItem>()
        val options = listOf(
            R.drawable.ico_gen1,
            R.drawable.ico_gen2,
            R.drawable.ico_gen3,
            R.drawable.ico_gen4,
            R.drawable.ico_gen5
        )
        for (locator in locatorList) {
            val distance = distance(
                location.latitude, location.longitude,
                locator.location.latitude ?: 0.0, locator.location.longitude ?: 0.0
            )
            if (distance < 1000) {
                val iconResId = when (locator.category.split("/").getOrNull(6) ?: options.random()) {
                    "Musica" -> R.drawable.ico_musica
                    "DanzaBaile" -> R.drawable.ico_danzabaile
                    "CursosTalleres" -> R.drawable.ico_cursostalleres
                    "TeatroPerformance" -> R.drawable.ico_teatro
                    "ActividadesCalleArteUrbano" -> R.drawable.ico_arteurbano
                    "CuentacuentosTiteresMarionetas" -> R.drawable.ico_cuentacuentos
                    "ComemoracionesHomenajes" -> R.drawable.ico_homenaje
                    "ConferenciasColoquios" -> R.drawable.ico_conferencias
                    "1ciudad21distritos" -> R.drawable.ico_ciudaddistritos
                    "ExcursionesItinerariosVisitas" -> R.drawable.ico_visitas
                    "ItinerariosOtrasActividadesAmbientales" -> R.drawable.ico_ambientales
                    "ClubesLectura" -> R.drawable.ico_lectura
                    "RecitalesPresentacionesActosLiterarios" -> R.drawable.ico_recitales
                    "Exposiciones" -> R.drawable.ico_exposiciones
                    "Campamentos" -> R.drawable.ico_campamentos
                    "CineActividadesAudiovisuales" -> R.drawable.ico_cine
                    "CircoMagia" -> R.drawable.ico_circo
                    "ProgramacionDestacadaAgendaCultura" -> R.drawable.ico_cultura
                    "ActividadesDeportivas" -> R.drawable.ico_deportes
                    "EnLinea" -> R.drawable.ico_enlinea
                    else -> options.random()
                }
                val menuItem = LocationMenuItem(locator.id, locator.title, iconResId, locator.dstart, locator.dfinish)
                menuItems.add(menuItem)
            }
        }
        if (menuItems.isEmpty()){
            menuItems.add(LocationMenuItem("","No se han encontrado actividades cercanas", R.drawable.ic_baseline_cancel_24, "", ""))
        }
        val adapter = LocationMenuAdapter(menuItems, object : LocationMenuAdapter.OnItemClickListener {
            override fun onItemClick(position: Int,item: LocationMenuItem) {
                val marker = markers["${item.id} ${item.title}"]
                if (marker != null) {
                    infoFragment.setMarker(marker, locatorList)
                    requireActivity().supportFragmentManager.beginTransaction()
                        .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
                        .replace(R.id.frame, infoFragment)
                        .addToBackStack(null)
                        .commit()
                    bottomSheetDialog.dismiss()
                }
            }
        })
        view.findViewById<RecyclerView>(R.id.location_menu_recycler_view).apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }
        val bottomSheetBehavior = BottomSheetBehavior.from(view.parent as View)
        bottomSheetBehavior.peekHeight = resources.getDimensionPixelSize(R.dimen.location_menu_height)
        bottomSheetDialog.show()
    }

    private fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val ratio = 6371
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = (sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2))
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (ratio * c * 1000).toFloat()
    }
}
