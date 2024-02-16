package net.opendasharchive.openarchive.util

import android.util.SparseArray
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter

/*
   Extension of FragmentStatePagerAdapter which intelligently caches
   all active fragments and manages the fragment lifecycles.
   Usage involves extending from SmartFragmentStatePagerAdapter as you would any other PagerAdapter.
*/
abstract class SmartFragmentStatePagerAdapter(
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle
) :
    FragmentStateAdapter(fragmentManager, lifecycle) {

    // Sparse array to keep track of registered fragments in memory
    private val registeredFragments = SparseArray<Fragment>()

    override fun getItemCount(): Int = registeredFragments.size()

    // Returns the fragment for the position (if instantiated)
    fun getRegisteredFragment(position: Int): Fragment? {
        return registeredFragments[position]
    }

    override fun createFragment(position: Int): Fragment {
        val fragment = createRegisteredFragment(position)
        registeredFragments[position] = fragment
        return fragment
    }

    abstract fun createRegisteredFragment(position: Int): Fragment
}
