package com.yehu.liquid

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import java.util.Calendar
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PromptDelegate
import org.mozilla.geckoview.GeckoSession.PromptDelegate.PromptResponse

/* Los avisos de las páginas: alert(), confirm(), listas <select>, fechas, contraseñas, archivos… */
class Avisos(
    private val actividad: () -> Activity?,
    private val anfitrion: () -> Anfitrion?
) : PromptDelegate {

    private fun dialogo(): AlertDialog.Builder? = actividad()?.let { AlertDialog.Builder(it) }

    /* Si el diálogo se cierra sin elegir, se le avisa a la página */
    private fun cerrarCon(d: AlertDialog.Builder, prompt: PromptDelegate.BasePrompt, r: GeckoResult<PromptResponse>) {
        d.setOnDismissListener { if (!prompt.isComplete) r.complete(prompt.dismiss()) }
    }

    override fun onAlertPrompt(session: GeckoSession, prompt: PromptDelegate.AlertPrompt): GeckoResult<PromptResponse>? {
        val d = dialogo() ?: return GeckoResult.fromValue(prompt.dismiss())
        val r = GeckoResult<PromptResponse>()
        d.setTitle(prompt.title).setMessage(prompt.message)
            .setPositiveButton("Aceptar") { _, _ -> r.complete(prompt.dismiss()) }
        cerrarCon(d, prompt, r)
        d.show()
        return r
    }

    override fun onButtonPrompt(session: GeckoSession, prompt: PromptDelegate.ButtonPrompt): GeckoResult<PromptResponse>? {
        val d = dialogo() ?: return GeckoResult.fromValue(prompt.dismiss())
        val r = GeckoResult<PromptResponse>()
        d.setTitle(prompt.title).setMessage(prompt.message)
            .setPositiveButton("Aceptar") { _, _ -> r.complete(prompt.confirm(PromptDelegate.ButtonPrompt.Type.POSITIVE)) }
            .setNegativeButton("Cancelar") { _, _ -> r.complete(prompt.confirm(PromptDelegate.ButtonPrompt.Type.NEGATIVE)) }
        cerrarCon(d, prompt, r)
        d.show()
        return r
    }

    override fun onTextPrompt(session: GeckoSession, prompt: PromptDelegate.TextPrompt): GeckoResult<PromptResponse>? {
        val act = actividad() ?: return GeckoResult.fromValue(prompt.dismiss())
        val d = AlertDialog.Builder(act)
        val r = GeckoResult<PromptResponse>()
        val campo = EditText(act).apply { setText(prompt.defaultValue.orEmpty()) }
        d.setTitle(prompt.title).setMessage(prompt.message).setView(conMargen(act, campo))
            .setPositiveButton("Aceptar") { _, _ -> r.complete(prompt.confirm(campo.text.toString())) }
            .setNegativeButton("Cancelar") { _, _ -> r.complete(prompt.dismiss()) }
        cerrarCon(d, prompt, r)
        d.show()
        return r
    }

    /* Usuario y contraseña que pide el servidor */
    override fun onAuthPrompt(session: GeckoSession, prompt: PromptDelegate.AuthPrompt): GeckoResult<PromptResponse>? {
        val act = actividad() ?: return GeckoResult.fromValue(prompt.dismiss())
        val d = AlertDialog.Builder(act)
        val r = GeckoResult<PromptResponse>()
        val soloClave = (prompt.authOptions.flags and PromptDelegate.AuthPrompt.AuthOptions.Flags.ONLY_PASSWORD) != 0
        val usuario = EditText(act).apply {
            hint = "Usuario"
            setText(prompt.authOptions.username.orEmpty())
        }
        val clave = EditText(act).apply {
            hint = "Contraseña"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val caja = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            if (!soloClave) addView(usuario)
            addView(clave)
        }
        d.setTitle(prompt.title ?: "Iniciar sesión").setMessage(prompt.message).setView(conMargen(act, caja))
            .setPositiveButton("Entrar") { _, _ ->
                r.complete(
                    if (soloClave) prompt.confirm(clave.text.toString())
                    else prompt.confirm(usuario.text.toString(), clave.text.toString())
                )
            }
            .setNegativeButton("Cancelar") { _, _ -> r.complete(prompt.dismiss()) }
        cerrarCon(d, prompt, r)
        d.show()
        return r
    }

    /* Listas <select> y menús de las páginas */
    override fun onChoicePrompt(session: GeckoSession, prompt: PromptDelegate.ChoicePrompt): GeckoResult<PromptResponse>? {
        val d = dialogo() ?: return GeckoResult.fromValue(prompt.dismiss())
        val r = GeckoResult<PromptResponse>()
        /* Los grupos (<optgroup>) se aplanan; sus títulos no se pueden elegir */
        val opciones = ArrayList<PromptDelegate.ChoicePrompt.Choice>()
        fun agregar(l: Array<PromptDelegate.ChoicePrompt.Choice>?) {
            l?.forEach { c ->
                if (c.separator) return@forEach
                val hijos = c.items
                if (hijos != null) agregar(hijos) else opciones.add(c)
            }
        }
        agregar(prompt.choices)
        val nombres = opciones.map { it.label }.toTypedArray()
        d.setTitle(prompt.title ?: prompt.message)
        if (prompt.type == PromptDelegate.ChoicePrompt.Type.MULTIPLE) {
            val marcadas = opciones.map { it.selected }.toBooleanArray()
            d.setMultiChoiceItems(nombres, marcadas) { _, i, si -> marcadas[i] = si }
                .setPositiveButton("Aceptar") { _, _ ->
                    r.complete(prompt.confirm(opciones.filterIndexed { i, c -> marcadas[i] && !c.disabled }.toTypedArray()))
                }
                .setNegativeButton("Cancelar") { _, _ -> r.complete(prompt.dismiss()) }
        } else {
            val elegida = opciones.indexOfFirst { it.selected }
            d.setSingleChoiceItems(nombres, elegida) { dlg, i ->
                val c = opciones[i]
                if (!c.disabled) {
                    r.complete(prompt.confirm(c))
                    dlg.dismiss()
                }
            }
        }
        cerrarCon(d, prompt, r)
        d.show()
        return r
    }

    /* Fechas y horas de los formularios */
    override fun onDateTimePrompt(session: GeckoSession, prompt: PromptDelegate.DateTimePrompt): GeckoResult<PromptResponse>? {
        val act = actividad() ?: return GeckoResult.fromValue(prompt.dismiss())
        val r = GeckoResult<PromptResponse>()
        val c = Calendar.getInstance()
        val tipo = prompt.type
        fun fecha(alElegir: (Int, Int, Int) -> Unit) {
            val dp = DatePickerDialog(act, { _, a, m, d -> alElegir(a, m, d) }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
            dp.setOnCancelListener { if (!prompt.isComplete) r.complete(prompt.dismiss()) }
            dp.show()
        }
        fun hora(alElegir: (Int, Int) -> Unit) {
            val tp = TimePickerDialog(act, { _, h, m -> alElegir(h, m) }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true)
            tp.setOnCancelListener { if (!prompt.isComplete) r.complete(prompt.dismiss()) }
            tp.show()
        }
        when (tipo) {
            PromptDelegate.DateTimePrompt.Type.DATE -> fecha { a, m, d -> r.complete(prompt.confirm("%04d-%02d-%02d".format(a, m + 1, d))) }
            PromptDelegate.DateTimePrompt.Type.MONTH -> fecha { a, m, _ -> r.complete(prompt.confirm("%04d-%02d".format(a, m + 1))) }
            PromptDelegate.DateTimePrompt.Type.WEEK -> fecha { a, m, d ->
                val k = Calendar.getInstance().apply {
                    minimalDaysInFirstWeek = 4
                    firstDayOfWeek = Calendar.MONDAY
                    set(a, m, d)
                }
                r.complete(prompt.confirm("%04d-W%02d".format(k.getWeekYear(), k.get(Calendar.WEEK_OF_YEAR))))
            }
            PromptDelegate.DateTimePrompt.Type.TIME -> hora { h, m -> r.complete(prompt.confirm("%02d:%02d".format(h, m))) }
            else -> fecha { a, m, d ->
                hora { h, mi -> r.complete(prompt.confirm("%04d-%02d-%02dT%02d:%02d".format(a, m + 1, d, h, mi))) }
            }
        }
        return r
    }

    override fun onColorPrompt(session: GeckoSession, prompt: PromptDelegate.ColorPrompt): GeckoResult<PromptResponse>? {
        val d = dialogo() ?: return GeckoResult.fromValue(prompt.dismiss())
        val r = GeckoResult<PromptResponse>()
        val colores = listOf(
            "Negro" to "#000000", "Blanco" to "#ffffff", "Gris" to "#808080", "Rojo" to "#e53935",
            "Naranja" to "#fb8c00", "Amarillo" to "#fdd835", "Verde" to "#43a047", "Azul" to "#1e88e5",
            "Morado" to "#8e24aa", "Rosa" to "#d81b60"
        )
        d.setTitle("Elige un color").setItems(colores.map { it.first }.toTypedArray()) { _, i ->
            r.complete(prompt.confirm(colores[i].second))
        }
        cerrarCon(d, prompt, r)
        d.show()
        return r
    }

    override fun onFilePrompt(session: GeckoSession, prompt: PromptDelegate.FilePrompt): GeckoResult<PromptResponse>? {
        val a = anfitrion() ?: return GeckoResult.fromValue(prompt.dismiss())
        val r = GeckoResult<PromptResponse>()
        a.elegirArchivos(prompt, r)
        return r
    }

    /* "¿Salir de esta página?" cuando un formulario tiene cambios */
    override fun onBeforeUnloadPrompt(session: GeckoSession, prompt: PromptDelegate.BeforeUnloadPrompt): GeckoResult<PromptResponse>? {
        val d = dialogo() ?: return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW))
        val r = GeckoResult<PromptResponse>()
        d.setTitle("¿Salir de esta página?").setMessage("Es posible que no se guarden los cambios que hiciste.")
            .setPositiveButton("Salir") { _, _ -> r.complete(prompt.confirm(AllowOrDeny.ALLOW)) }
            .setNegativeButton("Quedarme") { _, _ -> r.complete(prompt.confirm(AllowOrDeny.DENY)) }
        cerrarCon(d, prompt, r)
        d.show()
        return r
    }

    override fun onRepostConfirmPrompt(session: GeckoSession, prompt: PromptDelegate.RepostConfirmPrompt): GeckoResult<PromptResponse>? =
        GeckoResult.fromValue(prompt.confirm(AllowOrDeny.ALLOW))

    /* Ventanas emergentes que la página abre sola: se bloquean */
    override fun onPopupPrompt(session: GeckoSession, prompt: PromptDelegate.PopupPrompt): GeckoResult<PromptResponse>? {
        actividad()?.let { Toast.makeText(it, "Se bloqueó una ventana emergente", Toast.LENGTH_SHORT).show() }
        return GeckoResult.fromValue(prompt.confirm(AllowOrDeny.DENY))
    }

    /* El botón "Compartir" de las páginas */
    override fun onSharePrompt(session: GeckoSession, prompt: PromptDelegate.SharePrompt): GeckoResult<PromptResponse>? {
        val act = actividad() ?: return GeckoResult.fromValue(prompt.dismiss())
        val texto = listOfNotNull(prompt.text, prompt.uri).joinToString(" ")
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto)
        prompt.title?.let { i.putExtra(Intent.EXTRA_SUBJECT, it) }
        return try {
            act.startActivity(Intent.createChooser(i, null))
            GeckoResult.fromValue(prompt.confirm(PromptDelegate.SharePrompt.Result.SUCCESS))
        } catch (e: Exception) {
            GeckoResult.fromValue(prompt.confirm(PromptDelegate.SharePrompt.Result.FAILURE))
        }
    }

    private fun conMargen(act: Activity, v: android.view.View): android.view.View {
        val m = (20 * act.resources.displayMetrics.density).toInt()
        return LinearLayout(act).apply {
            setPadding(m, m / 2, m, 0)
            addView(v, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }
}
