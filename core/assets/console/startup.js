// Стартовый скрипт New Console (выполняется при загрузке клиента, после инициализации консоли).
// Оригинальный startup.js мода не парсился Rhino (redeclaration of formal parameter в readString)
// и ссылался на несуществующий в hardline-порте JSInterface - хелперы переписаны через ConsoleVars.
// Свой код можно дописывать прямо сюда... но проще пользоваться Events-скриптами консоли.

const modClassLoader = Vars.mods.mainLoader();

function classForName(name) {
	try {
		return Class.forName(name, true, modClassLoader);
	} catch (e) {
		Log.err(e);
		return null;
	}
}

function importCls(name) {
	return importClass(new Packages.rhino.NativeJavaClass(Vars.mods.scripts.scope, Class.forName(name, true, Vars.mods.mainLoader())))
}

function readString(path, fallback) {
	if (fallback === undefined) fallback = "invalid/nonexistent file";
	let file = Vars.tree.get(path);

	if (!file.exists()) return fallback;

	try {
		return file.readString();
	} catch (e) {
		return fallback;
	}
}

function NCHelp() {
	println(readString("console/startup.js-help"));
}

// NewConsole - доступ к консоли из скриптов: NewConsole.getCurrentConsole().addLog("..."), etc.
const NewConsole = ConsoleVars;
const _buffer = ConsoleVars.getCurrentConsole().logBuffer;

const append = text => {
	_buffer.append(text);
	return null;
};
const println = text => {
	_buffer.append(text).append("\n");
	return null;
};
const backread = () => ConsoleVars.getCurrentConsole().backread();
