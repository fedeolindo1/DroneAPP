package com.scania.droneautofly.ui

import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.scania.droneautofly.R
import com.scania.droneautofly.model.DroneStatus
import com.scania.droneautofly.views.DronePathView

/**
 * Atividade para visualizar o caminho de voo do drone com animação
 */
class FlightPathActivity : AppCompatActivity() {

    private lateinit var dronePathView: DronePathView
    private lateinit var btnStartPause: Button
    private lateinit var btnReset: Button
    private var isAnimating = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flight_path)

        // Configurar toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Visualização do Caminho de Voo"

        // Inicializar componentes
        initUI()
        
        // Carregar dados do caminho de voo
        loadFlightPathData()
    }

    private fun initUI() {
        dronePathView = findViewById(R.id.drone_path_view)
        btnStartPause = findViewById(R.id.btn_start_pause)
        btnReset = findViewById(R.id.btn_reset)

        btnStartPause.setOnClickListener {
            if (isAnimating) {
                pauseAnimation()
            } else {
                startAnimation()
            }
        }

        btnReset.setOnClickListener {
            resetAnimation()
        }
    }

    private fun loadFlightPathData() {
        // Em uma implementação real, carregaríamos os dados do caminho
        // de voo a partir de um histórico ou missão atual
        // Por enquanto, usamos os dados de exemplo definidos na DronePathView
        
        // Também poderíamos adicionar pontos aqui, exemplo:
        // dronePathView.setHomePoint(100f, 100f)
        // dronePathView.addWaypoint(200f, 150f)
        // ...
    }

    private fun startAnimation() {
        dronePathView.startAnimation()
        btnStartPause.text = "Pausar"
        isAnimating = true
    }

    private fun pauseAnimation() {
        dronePathView.stopAnimation()
        btnStartPause.text = "Continuar"
        isAnimating = false
    }

    private fun resetAnimation() {
        dronePathView.clearPath()
        loadFlightPathData()
        btnStartPause.text = "Iniciar"
        isAnimating = false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onPause() {
        super.onPause()
        dronePathView.stopAnimation()
    }

    override fun onResume() {
        super.onResume()
        if (isAnimating) {
            dronePathView.startAnimation()
        }
    }
}