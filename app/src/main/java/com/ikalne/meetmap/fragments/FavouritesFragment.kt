package com.ikalne.meetmap.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.ktx.Firebase
import com.ikalne.meetmap.AdapterFLI
import com.ikalne.meetmap.R
import com.ikalne.meetmap.api.models.LocatorView
import com.ikalne.meetmap.databinding.FragmentFavouritesBinding
import com.ikalne.meetmap.databinding.FragmentInfoActivityBinding
import com.ikalne.meetmap.model.CustomInfoWindowAdapter
import com.ikalne.meetmap.model.FLI
import com.ikalne.meetmap.model.LocationMenuItem
import com.ikalne.meetmap.viewmodels.MadridViewModel
import java.net.IDN

private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

class FavouritesFragment : Fragment(), AdapterFLI.OnItemClickListener {
    private var param1: String? = null
    private var param2: String? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var fliArrayList: ArrayList<FLI>
    private lateinit var binding: FragmentFavouritesBinding
    val firebaseAuth = FirebaseAuth.getInstance()
    private lateinit var adapterFLI: AdapterFLI

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadFavouriteActs()

        // Crear y configurar el RecyclerView
        recyclerView = binding.favouriteRv
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapterFLI = AdapterFLI(requireContext(), fliArrayList)
        adapterFLI.clickListener = this
        recyclerView.adapter = adapterFLI
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflar el diseño para este fragmento
        binding = FragmentFavouritesBinding.inflate(inflater, container, false)
        return binding.root
    }

    companion object {
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            FavouritesFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }

    private fun loadFavouriteActs(){
        fliArrayList = ArrayList()
        val ref = FirebaseDatabase.getInstance().getReference("users")
        ref.child(firebaseAuth.uid!!).child("Favourites")
            .addValueEventListener(object: ValueEventListener{
                @SuppressLint("NotifyDataSetChanged")
                override fun onDataChange(snapshot: DataSnapshot) {
                    fliArrayList.clear()
                    for(ds in snapshot.children){
                        val aid = "${ds.child("ID").value}"
                        val FLI = FLI()
                        FLI.id = aid
                        fliArrayList.add(FLI)
                    }
                    adapterFLI.notifyDataSetChanged()
                }
                override fun onCancelled(error: DatabaseError) {
                }
            })
    }

    override fun onItemClick(position: Int,item: FLI) {
        val marker = MapFragment.markers["${item.id} ${item.titulo}"]
        if (marker != null) {
            val infoFragment = InfoActivityFragment()
            infoFragment.setMarker(marker, MapFragment.locatorList)
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.frame, infoFragment)
                .addToBackStack(null)
                .commit()
        }
    }
}
