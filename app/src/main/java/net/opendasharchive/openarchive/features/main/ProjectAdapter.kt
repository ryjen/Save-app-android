package net.opendasharchive.openarchive.features.main

import android.os.Parcelable
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import net.opendasharchive.openarchive.db.Project
import net.opendasharchive.openarchive.features.settings.SettingsFragment
import net.opendasharchive.openarchive.util.SmartFragmentStatePagerAdapter
import timber.log.Timber
import kotlin.math.max

class ProjectAdapter(fragmentManager: FragmentManager, lifecycle: Lifecycle) : SmartFragmentStatePagerAdapter(fragmentManager, lifecycle) {

    var projects = listOf<Project>()
        private set

    override fun createRegisteredFragment(position: Int): Fragment {
        if (position == settingsIndex) {
            return SettingsFragment()
        }

        val project = getProject(position)

        return MainMediaFragment.newInstance(project?.id ?: -1)
    }

    override fun getItemCount(): Int {
        return max(1, projects.size) + 1
    }

    fun getProject(i: Int): Project? {
        return if (i > -1 && i < projects.size) projects[i] else null
    }

    val settingsIndex: Int
        get() = itemCount - 1

    fun updateData(projects: List<Project>) {
        this.projects = projects

        notifyDataSetChanged()
    }

    fun getIndex(project: Project?): Int {
        if (project == null) {
            return 0
        }

        return (projects.indexOf(project)).coerceAtLeast(0)
    }

    fun getRegisteredMediaFragment(position: Int): MainMediaFragment? {
        return getRegisteredFragment(position) as? MainMediaFragment
    }
}
