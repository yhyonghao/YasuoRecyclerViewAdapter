package com.fusion_nex_gen.yasuorvadapter

import android.content.Context
import android.util.SparseArray
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.databinding.OnRebindCallback
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import androidx.recyclerview.widget.RecyclerView
import com.fusion_nex_gen.yasuorvadapter.holder.RecyclerDataBindingHolder
import com.fusion_nex_gen.yasuorvadapter.interfaces.Listener
import com.fusion_nex_gen.yasuorvadapter.interfaces.ViewHolderBindListenerForDataBinding
import com.fusion_nex_gen.yasuorvadapter.interfaces.ViewHolderCreateListenerForDataBinding
import kotlin.reflect.KClass

/******使用viewDataBinding******/

/**
 * 绑定adapter
 * @param context Context 对象
 * @param context Context object
 * @param life LifecycleOwner object
 * @param rvListener 绑定Adapter实体之前需要做的操作
 * @param rvListener What to do before binding adapter entity
 */
inline fun <T : Any> RecyclerView.adapterDataBinding(
    context: Context,
    life: LifecycleOwner,
    itemList: MutableList<T>,
    headerItemList: MutableList<T> = ObList(),
    footerItemList: MutableList<T> = ObList(),
    rvListener: YasuoRVDataBindingAdapter<T>.() -> YasuoRVDataBindingAdapter<T>
): YasuoRVDataBindingAdapter<T> {
    return YasuoRVDataBindingAdapter(context, life, itemList, headerItemList, footerItemList).bindLife().rvListener()
        .attach(this)
}

/**
 * 绑定adapter
 * @param adapter Adapter实体
 * @param adapter Adapter实体 entity
 * @param rvListener 绑定Adapter实体之前需要做的操作
 * @param rvListener What to do before binding adapter entity
 */
inline fun <T : Any> RecyclerView.adapterDataBinding(
    adapter: YasuoRVDataBindingAdapter<T>,
    rvListener: YasuoRVDataBindingAdapter<T>.() -> YasuoRVDataBindingAdapter<T>
) {
    adapter.bindLife().rvListener().attach(this)
}

open class YasuoRVDataBindingAdapter<T : Any>(
    context: Context,
    private val life: LifecycleOwner,
    itemList: MutableList<T> = ObList(),
    headerItemList: MutableList<T> = ObList(),
    footerItemList: MutableList<T> = ObList(),
) : YasuoBaseRVAdapter<T, RecyclerDataBindingHolder<ViewDataBinding>>(context, itemList, headerItemList, footerItemList), LifecycleObserver {

    /**
     * 如果为true，那么布局中的variableId默认为BR.item，可以提升性能
     */
    var variableIdIsDefault = true

    init {
        //如果是使用的ObservableArrayList，那么需要注册监听
        if (this.itemList is ObList<T>) {
            (this.itemList as ObList<T>).addOnListChangedCallback(itemListListener)
        }
        if (this.headerItemList is ObList<T>) {
            (this.headerItemList as ObList<T>).addOnListChangedCallback(headerListListener)
        }
        if (this.footerItemList is ObList<T>) {
            (this.footerItemList as ObList<T>).addOnListChangedCallback(footerListListener)
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun itemListRemoveListener() {
        if (this.itemList is ObList<T>) {
            (this.itemList as ObList<T>).removeOnListChangedCallback(itemListListener)
        }
        if (this.headerItemList is ObList<T>) {
            (this.headerItemList as ObList<T>).removeOnListChangedCallback(headerListListener)
        }
        if (this.footerItemList is ObList<T>) {
            (this.footerItemList as ObList<T>).removeOnListChangedCallback(footerListListener)
        }
    }

    /**
     * 绑定生命周期，初始化adapter之后必须调用
     */
    fun bindLife(): YasuoRVDataBindingAdapter<T> {
        life.lifecycle.addObserver(this)
        return this
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        life.lifecycle.removeObserver(this)
    }

    //内部holder创建的监听集合
    private val innerHolderCreateListenerMap: SparseArray<ViewHolderCreateListenerForDataBinding<RecyclerDataBindingHolder<ViewDataBinding>>> =
        SparseArray()

    //内部holder绑定的监听集合
    private val innerHolderBindListenerMap: SparseArray<ViewHolderBindListenerForDataBinding<RecyclerDataBindingHolder<ViewDataBinding>>> =
        SparseArray()

    override fun <L : Listener<RecyclerDataBindingHolder<ViewDataBinding>>> setHolderCreateListener(type: Int, listener: L) {
        innerHolderCreateListenerMap.put(type, listener as ViewHolderCreateListenerForDataBinding<RecyclerDataBindingHolder<ViewDataBinding>>)
    }

    override fun <L : Listener<RecyclerDataBindingHolder<ViewDataBinding>>> setHolderBindListener(type: Int, listener: L) {
        innerHolderBindListenerMap.put(type, listener as ViewHolderBindListenerForDataBinding<RecyclerDataBindingHolder<ViewDataBinding>>)
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerDataBindingHolder<ViewDataBinding> {
        val binding = DataBindingUtil.inflate<ViewDataBinding>(inflater, viewType, parent, false)
        val holder = RecyclerDataBindingHolder(binding)
        binding.addOnRebindCallback(object : OnRebindCallback<ViewDataBinding>() {
            override fun onPreBind(binding: ViewDataBinding): Boolean = let {
                recyclerView!!.isComputingLayout
            }

            override fun onCanceled(binding: ViewDataBinding) {
                if (recyclerView!!.isComputingLayout) {
                    return
                }
                val position = holder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    notifyItemChanged(position, dataInvalidation)
                }
            }
        })
        //执行holder创建时的监听
        innerHolderCreateListenerMap[viewType]?.onCreateViewHolder(holder, binding)
        return holder
    }

    override fun onBindViewHolder(holder: RecyclerDataBindingHolder<ViewDataBinding>, position: Int) {
        //非禁用全局监听的布局才执行
        if (!disableGlobalItemHolderListenerType(holder.itemViewType)) {
            //执行之前判断非空
            getGlobalItemHolderListener()?.invoke(holder)
        }
        when {
            //判断是全屏布局
            isEmptyLayoutMode() -> holder.binding.setVariable(BR.item, emptyLayoutItem)
            //普通item
            inAllList(position) -> {
                //如果使用默认variableId
                if (variableIdIsDefault) {
                    holder.binding.setVariable(BR.item, getItem(position))
                } else {
                    //否则去查找自定义variableId
                    val item = getItem(position)
                    val itemType = itemTypes[item::class]
                    if (itemType != null) {
                        if (itemType.variableId != null) {
                            holder.binding.setVariable(itemType.variableId, item)
                        }
                    } else {
                        throw Exception("A layout of the corresponding type was not found")
                    }
                }
            }
            //loadMoreView
            hasLoadMore() -> holder.binding.setVariable(BR.item, loadMoreLayoutItem)
            else -> throw RuntimeException("onBindViewHolder position error! position = $position")
        }
        innerHolderBindListenerMap[holder.itemViewType]?.onBindViewHolder(holder, holder.binding)
        holder.binding.lifecycleOwner = life
        holder.binding.executePendingBindings()
    }
}

//TODO 将create和bind合并成一个方法

/**
 * View Holder创建时触发
 * @param itemLayoutId itemView布局id
 */
inline fun <T, VB : ViewDataBinding, Adapter : YasuoRVDataBindingAdapter<T>> Adapter.onHolderCreate(
    itemLayoutId: Int,
    bindingType: KClass<VB>,
    crossinline block: VB.(holder: RecyclerDataBindingHolder<ViewDataBinding>) -> Unit
): Adapter {
    setHolderCreateListener(itemLayoutId, object : ViewHolderCreateListenerForDataBinding<RecyclerDataBindingHolder<ViewDataBinding>> {
        override fun onCreateViewHolder(holder: RecyclerDataBindingHolder<ViewDataBinding>, binding: ViewDataBinding) {
            (binding as VB).block(holder)
        }
    })
    return this
}

/**
 * 建立数据类与布局文件之间的匹配关系，payloads
 * @param itemLayoutId itemView布局id
 * @param kClass Item::class
 * @param bindingType ViewDataBinding::class
 * @param bind 绑定监听这个viewHolder的所有事件
 */
fun <T, VB : ViewDataBinding, Adapter : YasuoRVDataBindingAdapter<T>> Adapter.onHolderDataBindingAndPayloads(
    itemLayoutId: Int,
    kClass: KClass<*>,
    bindingType: KClass<VB>,
    customItemBR: Int = BR.item,
    bind: (VB.(holder: RecyclerDataBindingHolder<ViewDataBinding>, payloads: List<Any>?) -> Unit)? = null
): Adapter {
    itemTypes[kClass] = ItemType(itemLayoutId, customItemBR)
    if (bind != null) {
        setHolderBindListener(itemLayoutId, object : ViewHolderBindListenerForDataBinding<RecyclerDataBindingHolder<ViewDataBinding>> {
            override fun onBindViewHolder(holder: RecyclerDataBindingHolder<ViewDataBinding>, binding: ViewDataBinding, payloads: List<Any>?) {
                (binding as VB).bind(holder, payloads)
            }
        })
    }
    return this
}

/**
 * 建立数据类与布局文件之间的匹配关系
 * @param itemLayoutId itemView布局id
 * @param itemClass Item::class
 * @param bindingClass ViewDataBinding::class
 * @param bind 绑定监听这个viewHolder的所有事件
 */
fun <T, VB : ViewDataBinding, Adapter : YasuoRVDataBindingAdapter<T>> Adapter.holderBind(
    itemLayoutId: Int,
    itemClass: KClass<*>,
    bindingClass: KClass<VB>,
    customItemBR: Int = BR.item,
    bind: (VB.(holder: RecyclerDataBindingHolder<ViewDataBinding>) -> Unit)? = null
): Adapter {
    itemTypes[itemClass] = ItemType(itemLayoutId, customItemBR)
    if (bind != null) {
        setHolderBindListener(itemLayoutId, object : ViewHolderBindListenerForDataBinding<RecyclerDataBindingHolder<ViewDataBinding>> {
            override fun onBindViewHolder(holder: RecyclerDataBindingHolder<ViewDataBinding>, binding: ViewDataBinding, payloads: List<Any>?) {
                (binding as VB).bind(holder)
            }
        })
    }
    return this
}

/**
 * 建立loadMore数据类与布局文件之间的匹配关系
 * @param loadMoreLayoutId 加载更多布局id
 * @param loadMoreLayoutItem 加载更多布局对应的实体
 * @param bind 绑定监听这个viewHolder的所有事件
 */
fun <T : Any, VB : ViewDataBinding, Adapter : YasuoRVDataBindingAdapter<T>> Adapter.holderBindLoadMore(
    loadMoreLayoutId: Int,
    loadMoreLayoutItem: T,
    bindingClass: KClass<VB>,
    customItemBR: Int = BR.item,
    bind: (VB.(holder: RecyclerDataBindingHolder<ViewDataBinding>) -> Unit)? = null
): Adapter {
    this.loadMoreLayoutId = loadMoreLayoutId
    this.loadMoreLayoutItem = loadMoreLayoutItem
    itemTypes[loadMoreLayoutItem::class] = ItemType(loadMoreLayoutId, customItemBR)
    if (bind != null) {
        setHolderBindListener(loadMoreLayoutId, object : ViewHolderBindListenerForDataBinding<RecyclerDataBindingHolder<ViewDataBinding>> {
            override fun onBindViewHolder(holder: RecyclerDataBindingHolder<ViewDataBinding>, binding: ViewDataBinding, payloads: List<Any>?) {
                (binding as VB).bind(holder)
            }
        })
    }
    return this
}

