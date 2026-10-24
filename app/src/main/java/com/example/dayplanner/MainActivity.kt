package com.example.dayplanner

import android.app.*
import android.os.Bundle
import android.content.*
import android.database.sqlite.*
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.*
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

private val INK = Color.rgb(28, 41, 60)
private val MUTED = Color.rgb(111, 126, 147)
private val BLUE = Color.rgb(52, 104, 234)
private val PAGE = Color.rgb(247, 249, 253)
private val LINE = Color.rgb(229, 235, 245)

class PlannerDb(ctx: Context): SQLiteOpenHelper(ctx, "planner.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE templates(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL)")
        db.execSQL("CREATE TABLE template_items(id INTEGER PRIMARY KEY AUTOINCREMENT,template_id INTEGER NOT NULL,title TEXT NOT NULL,minutes INTEGER NOT NULL,note TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE plans(id INTEGER PRIMARY KEY AUTOINCREMENT,day TEXT NOT NULL,start_time TEXT NOT NULL,title TEXT NOT NULL,minutes INTEGER NOT NULL,note TEXT NOT NULL DEFAULT '',done INTEGER NOT NULL DEFAULT 0,start_offset INTEGER NOT NULL DEFAULT -1)")
        db.execSQL("INSERT INTO templates(name) VALUES('日常计划')")
        listOf("早餐" to 30,"专注学习" to 120,"休息" to 30,"运动" to 60).forEach { (title, mins) ->
            db.execSQL("INSERT INTO template_items(template_id,title,minutes,note) VALUES(1,?,?,'')", arrayOf(title, mins))
        }
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE plans ADD COLUMN start_offset INTEGER NOT NULL DEFAULT -1")
    }
}

data class Task(val id:Long, val title:String, val minutes:Int, val note:String, val done:Boolean=false, val offset:Int=-1, val legacyTime:String="")

class MainActivity: Activity() {
    private lateinit var db: PlannerDb
    private lateinit var root: LinearLayout
    private var day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    private var start = "08:00"
    private var selectedTemplate = 1L
    private val pad get() = (resources.displayMetrics.density * 16).toInt()
    private fun dp(v:Int) = (resources.displayMetrics.density*v).toInt()
    override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); window.statusBarColor=PAGE; window.navigationBarColor=Color.WHITE; window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR; db=PlannerDb(this); showToday() }
    private fun shape(color:Int, radius:Int=18, stroke:Int?=null):GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius=dp(radius).toFloat(); if(stroke!=null) setStroke(dp(1),stroke) }
    private fun label(s:String, size:Float=15f, bold:Boolean=false, color:Int=INK):TextView = TextView(this).apply { text=s; textSize=size; setTextColor(color); if(bold) setTypeface(null,Typeface.BOLD); gravity=Gravity.CENTER_VERTICAL }
    private fun column():LinearLayout = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
    private fun row():LinearLayout = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
    private fun addGap(parent:LinearLayout, height:Int=12) { parent.addView(View(this), LinearLayout.LayoutParams(1,dp(height))) }
    private fun card():LinearLayout = column().apply { background=shape(Color.WHITE,20,LINE); setPadding(dp(16),dp(16),dp(16),dp(16)); elevation=dp(1).toFloat(); root.addView(this,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(12)}) }
    private fun action(text:String, parent:LinearLayout=root, primary:Boolean=false, onClick:()->Unit) {
        val b=TextView(this).apply { this.text=text; textSize=14f; setTypeface(null,Typeface.BOLD); gravity=Gravity.CENTER; setTextColor(if(primary)Color.WHITE else BLUE); background=shape(if(primary)BLUE else Color.rgb(237,243,255),13); setPadding(dp(14),dp(13),dp(14),dp(13)); setOnClickListener{onClick()} }
        parent.addView(b,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(8)})
    }
    private fun init(section:String) {
        val outer=column().apply{setBackgroundColor(PAGE)}
        val scroll=ScrollView(this).apply{isFillViewport=true; clipToPadding=false}
        root=column().apply{setPadding(dp(18),dp(24),dp(18),dp(28))}
        scroll.addView(root); outer.addView(scroll,LinearLayout.LayoutParams(-1,0,1f)); setContentView(outer)
        root.addView(label("DAYFLOW  /  离线日程",12f,true,BLUE)); addGap(root,6)
        root.addView(label(section,29f,true)); addGap(root,18)
        val nav=row(); val sections=listOf("今日计划","模板管理","历史回顾")
        sections.forEachIndexed { index,name ->
            val active=name==section
            val tab=label(name,13f,active,if(active)BLUE else MUTED).apply { gravity=Gravity.CENTER; setPadding(dp(6),dp(13),dp(6),dp(13)); background=shape(if(active)Color.rgb(230,238,255) else Color.WHITE,12); setOnClickListener{when(index){0->showToday();1->showTemplates();else->showHistory()}} }
            nav.addView(tab,LinearLayout.LayoutParams(0,-2,1f).apply{marginEnd=dp(5)})
        }
        root.addView(nav); addGap(root,18)
    }
    private fun field(hint:String, value:String="")=EditText(this).apply{setSingleLine(true);setText(value);this.hint=hint;textSize=15f;setPadding(dp(8),dp(10),dp(8),dp(10))}
    private fun ask(title:String, fields:List<EditText>, save:()->Unit){val body=column().apply{setPadding(dp(20),dp(8),dp(20),0)};fields.forEach{body.addView(it)};AlertDialog.Builder(this).setTitle(title).setView(body).setPositiveButton("保存"){_,_->save()}.setNegativeButton("取消",null).show()}
    private fun templates():List<Pair<Long,String>> = mutableListOf<Pair<Long,String>>().also { out-> db.readableDatabase.rawQuery("SELECT id,name FROM templates ORDER BY id",null).use{c->while(c.moveToNext())out.add(c.getLong(0) to c.getString(1))} }
    private fun templateTasks(id:Long):List<Task> = mutableListOf<Task>().also{out->db.readableDatabase.rawQuery("SELECT id,title,minutes,note FROM template_items WHERE template_id=? ORDER BY id",arrayOf(id.toString())).use{c->while(c.moveToNext())out.add(Task(c.getLong(0),c.getString(1),c.getInt(2),c.getString(3)))} }
    private fun dayTasks():List<Task> = mutableListOf<Task>().also{out->db.readableDatabase.rawQuery("SELECT id,title,minutes,note,done,start_offset,start_time FROM plans WHERE day=? ORDER BY CASE WHEN start_offset>=0 THEN start_offset ELSE 99999 END,id",arrayOf(day)).use{c->while(c.moveToNext())out.add(Task(c.getLong(0),c.getString(1),c.getInt(2),c.getString(3),c.getInt(4)==1,c.getInt(5),c.getString(6)))} }
    private fun chooseDay(next:()->Unit){val p=day.split("-").map{it.toInt()};DatePickerDialog(this,{_,y,m,d->day="%04d-%02d-%02d".format(Locale.US,y,m+1,d);next()},p[0],p[1]-1,p[2]).show()}
    private fun chooseTime(next:()->Unit){val p=start.split(":").map{it.toInt()};TimePickerDialog(this,{_,h,m->start="%02d:%02d".format(Locale.US,h,m);next()},p[0],p[1],true).show()}
    private fun timeMinutes(s:String):Int? {if(!Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]").matches(s))return null;val p=s.split(":");return p[0].toInt()*60+p[1].toInt()}
    private fun displayTime(offset:Int):String { val dayIndex=offset/1440; val minutes=offset%1440; val prefix=if(dayIndex==0)"" else if(dayIndex==1)"次日 " else "第${dayIndex+1}天 ";return prefix+"%02d:%02d".format(Locale.US,minutes/60,minutes%60) }
    private fun taskStart(t:Task)=if(t.offset>=0)t.offset else timeMinutes(t.legacyTime)?:0
    private fun showToday(){init("我的每日计划")
        val setup=card();setup.addView(label("安排新的一天",19f,true));addGap(setup,12)
        action("日期  ·  $day   ▾",setup){chooseDay{showToday()}}
        action("开始时间  ·  $start   ▾",setup){chooseTime{showToday()}}
        val all=templates();if(all.none{it.first==selectedTemplate})selectedTemplate=all.firstOrNull()?.first?:0
        action("模板  ·  ${all.find{it.first==selectedTemplate}?.second?:"请创建模板"}   ▾",setup){if(all.isNotEmpty())AlertDialog.Builder(this).setItems(all.map{it.second}.toTypedArray()){_,i->selectedTemplate=all[i].first;showToday()}.show()}
        action("生成当天计划",setup,true){val items=templateTasks(selectedTemplate);if(items.isEmpty()){toast("请先给模板添加任务");return@action}
            val generate={val sql=db.writableDatabase;sql.beginTransaction();try{sql.delete("plans","day=?",arrayOf(day));var cursor=timeMinutes(start)?:480;items.forEach{t->val v=ContentValues().apply{put("day",day);put("start_time","%02d:%02d".format(Locale.US,(cursor/60)%24,cursor%60));put("start_offset",cursor);put("title",t.title);put("minutes",t.minutes);put("note",t.note)};sql.insert("plans",null,v);cursor+=t.minutes};sql.setTransactionSuccessful()}finally{sql.endTransaction()};showToday()}
            if(dayTasks().isNotEmpty())AlertDialog.Builder(this).setTitle("覆盖当天计划？").setMessage("这会覆盖当天任务、备注和打卡。其他日期不受影响。") .setPositiveButton("覆盖"){_,_->generate()}.setNegativeButton("取消",null).show() else generate()
        }
        val tasks=dayTasks();val done=tasks.count{it.done};val title=row();title.addView(label("计划时间轴",20f,true),LinearLayout.LayoutParams(0,-2,1f));title.addView(label("$done / ${tasks.size} 已完成",13f,false,BLUE));root.addView(title);addGap(root,12)
        if(tasks.isEmpty()){val empty=card();empty.addView(label("还没有安排计划\n选择模板和开始时间，点击生成。",15f,false,MUTED))}
        tasks.forEach{t->val c=card();val header=row();val startAt=taskStart(t);val endAt=startAt+t.minutes
            val check=CheckBox(this).apply{isChecked=t.done;buttonTintList=android.content.res.ColorStateList.valueOf(BLUE);setOnCheckedChangeListener{_,checked->val v=ContentValues().apply{put("done",if(checked)1 else 0)};db.writableDatabase.update("plans",v,"id=?",arrayOf(t.id.toString()))}}
            header.addView(check);val details=column();details.addView(label(t.title,17f,true));addGap(details,5);details.addView(label("${displayTime(startAt)}  —  ${displayTime(endAt)}  ·  ${t.minutes}分钟",13f,false,BLUE));header.addView(details,LinearLayout.LayoutParams(0,-2,1f));c.addView(header)
            if(t.note.isNotBlank()){addGap(c,8);c.addView(label("备注  ${t.note}",13f,false,MUTED))};addGap(c,10);action("编辑任务与备注  ✎",c){editPlan(t)}
        }
        if(tasks.any{taskStart(it)+it.minutes>=1440}){root.addView(label("☾  已自动延续到次日；历史记录仍归属 $day",13f,false,MUTED))}
    }
    private fun editPlan(t:Task){val title=field("任务名称",t.title);val minutes=field("时长（分钟）",t.minutes.toString()).apply{inputType=2};val note=field("备注",t.note);val offset=field("开始时间（如 23:30 或 次日 02:00）",displayTime(taskStart(t)))
        ask("编辑计划",listOf(title,minutes,note,offset)){
            val n=minutes.text.toString().toIntOrNull();val raw=offset.text.toString().trim();val next=if(raw.startsWith("次日 "))timeMinutes(raw.removePrefix("次日 "))?.plus(1440) else if(raw.startsWith("第")&&raw.contains("天 ")){val parts=raw.split("天 ",limit=2);val dayNum=parts[0].removePrefix("第").toIntOrNull();val mins=if(parts.size==2)timeMinutes(parts[1]) else null;if(dayNum!=null&&dayNum>0&&mins!=null)(dayNum-1)*1440+mins else null} else timeMinutes(raw)
            if(title.text.isBlank()||n==null||n<=0||next==null){toast("请填写有效名称、时长和时间");return@ask}
            val v=ContentValues().apply{put("title",title.text.toString());put("minutes",n);put("note",note.text.toString());put("start_offset",next);put("start_time","%02d:%02d".format(Locale.US,(next/60)%24,next%60))};db.writableDatabase.update("plans",v,"id=?",arrayOf(t.id.toString()));showToday()
        }
    }
    private fun showTemplates(){init("模板管理");action("＋ 新建模板",root,true){val name=field("模板名称");ask("新建模板",listOf(name)){if(name.text.isNotBlank()){val v=ContentValues().apply{put("name",name.text.toString())};selectedTemplate=db.writableDatabase.insert("templates",null,v);showTemplates()}}}
        templates().forEach{(id,name)->val c=card();c.addView(label(name,19f,true));addGap(c,10);templateTasks(id).forEach{t->action("${t.title}   ·   ${t.minutes}分钟   ✎",c){editTemplateTask(id,t)}};action("＋ 添加任务",c){editTemplateTask(id,null)};action("删除此模板",c){AlertDialog.Builder(this).setTitle("删除 $name？").setMessage("已生成的历史计划不会删除。") .setPositiveButton("删除"){_,_->db.writableDatabase.delete("template_items","template_id=?",arrayOf(id.toString()));db.writableDatabase.delete("templates","id=?",arrayOf(id.toString()));showTemplates()}.setNegativeButton("取消",null).show()}}
    }
    private fun editTemplateTask(templateId:Long,t:Task?){val title=field("任务名称",t?.title?:"");val mins=field("持续分钟",t?.minutes?.toString()?:"30").apply{inputType=2};val note=field("默认备注",t?.note?:"");ask(if(t==null)"添加模板任务" else "修改模板任务",listOf(title,mins,note)){val n=mins.text.toString().toIntOrNull();if(title.text.isBlank()||n==null||n<=0){toast("请填写有效名称和时长");return@ask};val v=ContentValues().apply{put("title",title.text.toString());put("minutes",n);put("note",note.text.toString());put("template_id",templateId)};if(t==null)db.writableDatabase.insert("template_items",null,v) else db.writableDatabase.update("template_items",v,"id=?",arrayOf(t.id.toString()));showTemplates()}}
    private fun showHistory(){init("历史回顾");action("查看日期  ·  $day   ▾",root){chooseDay{showHistory()}};val tasks=dayTasks();val summary=card();summary.addView(label("$day",19f,true));addGap(summary,8);summary.addView(label("完成 ${tasks.count{it.done}} / ${tasks.size} 项",15f,false,BLUE));if(tasks.isEmpty()){val c=card();c.addView(label("这一天没有保存的计划",15f,false,MUTED))};tasks.forEach{t->val c=card();c.addView(label("${if(t.done)"✓" else "○"}  ${t.title}",16f,true));addGap(c,5);c.addView(label("${displayTime(taskStart(t))} — ${displayTime(taskStart(t)+t.minutes)}",13f,false,BLUE));if(t.note.isNotBlank()){addGap(c,5);c.addView(label("备注  ${t.note}",13f,false,MUTED))}};action("打开这一天并编辑"){showToday()}}
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
}
