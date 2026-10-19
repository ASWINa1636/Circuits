package com.example.circuits.ui.theme

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filter
import android.widget.Filterable
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.circuits.R
import com.example.circuits.data.Datasheet
import java.util.Locale

class DatasheetAdapter(
    private var items: ArrayList<Datasheet>,
    private val clickListener: (Datasheet) -> Unit
) : RecyclerView.Adapter<DatasheetAdapter.ViewHolder>(), Filterable {

    // A copy of the original list to use for filtering
    private var datasheetListFull: ArrayList<Datasheet> = ArrayList(items)

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.tvComponentName)
        val description: TextView = itemView.findViewById(R.id.tvDescription)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_datasheet, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.name.text = item.componentName
        holder.description.text = item.description
        holder.itemView.setOnClickListener {
            clickListener(item)
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }

    // --- Filterable Implementation ---

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val filteredList = ArrayList<Datasheet>()
                if (constraint == null || constraint.isEmpty()) {
                    filteredList.addAll(datasheetListFull)
                } else {
                    val filterPattern = constraint.toString().lowercase(Locale.getDefault()).trim()
                    for (item in datasheetListFull) {
                        if (item.componentName.lowercase(Locale.getDefault()).contains(filterPattern) ||
                            item.description.lowercase(Locale.getDefault()).contains(filterPattern)) {
                            filteredList.add(item)
                        }
                    }
                }
                val results = FilterResults()
                results.values = filteredList
                return results
            }

            @SuppressLint("NotifyDataSetChanged")
            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                items.clear()
                items.addAll(results?.values as ArrayList<Datasheet>)
                notifyDataSetChanged()
            }
        }
    }

    /**
     * Call this from MainActivity to update the adapter's internal full list
     * when the data is first loaded.
     */
    @SuppressLint("NotifyDataSetChanged")
    fun updateFullList(newList: ArrayList<Datasheet>) {
        datasheetListFull = ArrayList(newList)
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }
}

