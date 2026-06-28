package leolee3914.real_lift;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Locale {

	public final static String MENU_TITLE_LIFT = "menu-title-lift";
	public final static String MENU_TITLE_LIFT_FAST_MODE = "menu-title-lift-fast-mode";
	public final static String SELECT_FLOOR = "select-floor";
	public final static String LIFT_MOVED = "lift-moved";
	public final static String HEIGHT = "height";
	public final static String CURRENT_HEIGHT = "current-height";
	public final static String HIGHEST = "highest";
	public final static String LOWEST = "lowest";
	public final static String LIFT_WAITING = "lift-waiting";
	public final static String STOPPED_LIFT = "stopped-lift";
	public final static String LIFT_ARRIVED = "lift-arrived";

	private static HashMap<String, String> data = new HashMap<>();

	public static String get ( String key ) {
		@Nullable String value = data.get(key);
		return (value != null) ? value : key;
	}

	public static void setData ( YamlConfiguration config ) {
		for ( String key : List.of(
			MENU_TITLE_LIFT,
			MENU_TITLE_LIFT_FAST_MODE,
			SELECT_FLOOR,
			LIFT_MOVED,
			HEIGHT,
			CURRENT_HEIGHT,
			HIGHEST,
			LOWEST,
			LIFT_WAITING,
			STOPPED_LIFT,
			LIFT_ARRIVED
		) ) {
			@Nullable String value = config.getString(key, null);
			if ( value != null ) {
				data.put(key, value);
			}
		}
	}

}