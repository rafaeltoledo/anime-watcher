package brunodles.animewatcher.player

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.databinding.DataBindingUtil
import android.net.Uri
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import brunodles.animewatcher.GenericAdapter
import brunodles.animewatcher.R
import brunodles.animewatcher.cast.Cast
import brunodles.animewatcher.databinding.ActivityVideoBinding
import brunodles.animewatcher.databinding.ItemEpisodeBinding
import brunodles.animewatcher.explorer.Episode
import brunodles.animewatcher.loadImageInto
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers

class PlayerActivity : AppCompatActivity() {

    companion object {

        val TAG = "PlayerActivity"
        val STATE_KEY = "explorer"
        val EXTRA_EPISODE = "episode"

        fun newIntent(context: Context, episode: Episode): Intent
                = Intent(context, PlayerActivity::class.java).putExtra(EXTRA_EPISODE, episode)

        fun newIntent(context: Context, link: String): Intent
                = Intent(context, PlayerActivity::class.java).setData(Uri.parse(link))
    }

    private lateinit var binding: ActivityVideoBinding
    private lateinit var player: Player
    private lateinit var cast: Cast
    private var adapter: GenericAdapter<Episode, ItemEpisodeBinding>? = null
    private var explorer: Episode? = null
    private val episodeController by lazy { EpisodeController(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_video)

        player = Player(this, binding.player)
        setupRecyclerView()

        cast = Cast(this, binding.mediaRouteButton)

        binding.playRemote.setOnClickListener {
            cast.playRemove(explorer, player.getCurrentPosition())
        }

        episodeController.findVideo(intent)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.io())
                .subscribeBy(onNext = {
                    it.video?.let { player.prepareVideo(it) }
                    binding.title.text = "${it.number} - ${it.description}"
                    adapter?.list = it.nextEpisodes ?: listOf()
                    explorer = it
                }, onError = {
                    if (binding.root != null) {
                        val snackbar = Snackbar.make(binding.root, "Failed to process the url, ${it.message}", Snackbar.LENGTH_INDEFINITE)
                        snackbar.setAction("Ok") { snackbar.dismiss() }
                        snackbar.show()
                    }
                    Log.e(TAG, "onResume.FindUrl: ", it)
                })
    }

    private fun setupRecyclerView() {
        val manager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        binding.nextEpisodes.layoutManager = manager
        adapter = GenericAdapter<Episode, ItemEpisodeBinding>(R.layout.item_episode) { viewHolder, item, index ->
            viewHolder.binder.description.text = "${item.number} - ${item.description}"
            loadImageInto(item.image, viewHolder.binder.image)
            viewHolder.binder.root.setOnClickListener {
                val intent = Intent(this, PlayerActivity::class.java)
                        .setData(Uri.parse(item.link))
                startActivity(intent)
            }
        }
        binding.nextEpisodes.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        cast.onResume()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (explorer != null)
            outState.putSerializable(STATE_KEY, explorer)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle?) {
        super.onRestoreInstanceState(savedInstanceState)
        if (savedInstanceState == null)
            return
        explorer = savedInstanceState.getSerializable(STATE_KEY) as Episode?
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        val landscape = newConfig?.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (landscape) {
            binding.otherContent.visibility = View.GONE
            val params = binding.player.layoutParams
            params?.height = ViewGroup.LayoutParams.MATCH_PARENT
            params?.width = ViewGroup.LayoutParams.MATCH_PARENT
            binding.player.layoutParams = params
            binding.player.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL)
            window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

            val decorView = window.decorView
            val uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            decorView.systemUiVisibility = uiOptions

        } else {
            binding.otherContent.visibility = View.VISIBLE
            val params = binding.player.layoutParams
            params?.height = ViewGroup.LayoutParams.WRAP_CONTENT
            params?.width = ViewGroup.LayoutParams.MATCH_PARENT
            binding.player.layoutParams = params
            binding.player.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH)
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)

            val decorView = window.decorView
            decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
        super.onConfigurationChanged(newConfig)
    }

    override fun onPause() {
        super.onPause()
        cast.onPause()
        player.stop()
    }

}
