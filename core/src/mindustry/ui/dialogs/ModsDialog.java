package mindustry.ui.dialogs;

import arc.*;
import arc.files.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.scene.*;
import arc.scene.style.*;
import arc.scene.ui.TextButton.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.util.Http.*;
import arc.util.io.*;
import arc.util.serialization.*;
<<<<<<< HEAD
import arc.util.serialization.Jval.*;
import mindustry.*;
import mindustry.client.ui.*;
import mindustry.client.utils.*;
import mindustry.core.*;
=======
>>>>>>> v159.3
import mindustry.ctype.*;
import mindustry.game.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.mod.*;
import mindustry.mod.Mods.*;
import mindustry.ui.*;

import java.util.*;

import static arc.Core.*;
import static mindustry.Vars.*;

public class ModsDialog extends BaseDialog{
    public ModBrowserDialog browser;

    protected float modImportProgress;
    protected boolean cancelledImport;
    protected BaseDialog currentContent;

<<<<<<< HEAD
    private BaseDialog browser;
    private Table browserTable;
    private int prompted, expected;
    private boolean autoUpdating; // Whether mods are currently being auto updated
    private float scroll = 0f;
=======
    protected float scroll = 0f;
    //only records mods that have a valid repo!
    protected ObjectMap<LoadedMod, ModListing> modToListing = new ObjectMap<>();
    protected ObjectSet<LoadedMod> withUpdates = new ObjectSet<>();
    protected @Nullable Element updaterElement;
>>>>>>> v159.3

    private final Runnable autoUpdaterHandler = () -> { // RUN THIS ON THE MAIN THREAD
        if (++prompted == expected) { // FINISHME: Awful
            autoUpdating = false;
            if (mods.requiresReload()){
                if (Core.settings.getInt("modautoupdate") == 2) reload();
                new Toast(5f).add("[accent]Mod updates found, they will be installed after restart.");
            } else new Toast(5f).add("[accent]No mod updates found.");
        }
    };
    private final ObjectMap<String, Runnable> onSuccess = new ObjectMap<>();

    public ModsDialog(){
        super("@mods");
        addCloseButton();

        buttons.button("@mods.guide", Icon.link, () -> Core.app.openURI(modGuideURL)).size(210, 64f);

        if(!mobile){
            buttons.button("@mods.openfolder", Icon.link, () -> Core.app.openFolder(modDirectory.absolutePath()));
        }

<<<<<<< HEAD
        buttons.button("@client.mods.updateactive", Icon.download, () -> mods.mods.each(m -> m.state == ModState.enabled && m.getRepo() != null && settings.getBool(m.massUpdateString(), true), m -> githubImportMod(m.getRepo(), m.isJava(), null, null)));

        shown(this::setup);
=======
        shown(() -> {
            setup();

            withUpdates.clear();
            refreshModUpdates();
        });
>>>>>>> v159.3
        onResize(this::setup);

        Events.on(ResizeEvent.class, event -> {
            if(currentContent != null){
                currentContent.hide();
                currentContent = null;
            }
        });

        hidden(() -> {
            if(mods.requiresReload()){
                mods.reload();
            }

<<<<<<< HEAD
        // Client mod auto updater
        Events.on(EventType.ClientLoadEvent.class, event -> {
            long hour = 1000 * 60 * 60;
            if (mods.mods.contains(LoadedMod::enabled) && Core.settings.getInt("modautoupdate") != 0 && (Time.timeSinceMillis(settings.getLong("lastmodupdate", hour + 1)) > hour)) {
                autoUpdating = true;
                Log.debug("Checking for mod updates @", Time.timeSinceMillis(settings.getLong("lastmodupdate", hour + 1)) / (60*1000f));
                Core.settings.put("lastmodupdate", Time.millis());
                for (Mods.LoadedMod mod : mods.mods.copy().shuffle()) { // Use shuffled mod list, if the user has more than 30 active mods, this will ensure that each is checked at least somewhat frequently FINISHME: This should take dependencies and requirements into account which we don't do currently
                    if (!mod.enabled() || mod.getRepo() == null || !settings.getBool(mod.autoUpdateString(), true)) continue;
                    if (expected++ >= 30) continue; // Only make up to 30 api requests
                    mod.isAutoUpdating = true;
                    githubImportMod(mod.getRepo(), mod.isJava(), null, mod.meta.version, autoUpdaterHandler);
                }
            } else Log.debug("Not updating mods, updated too recently / auto update is disabled / no enabled mods.");
        });
    }

    void modError(Throwable error){
        ui.loadfrag.hide();

        if(error instanceof NoSuchMethodError || Strings.getCauses(error).contains(t -> t.getMessage() != null && (t.getMessage().contains("trust anchor") || t.getMessage().contains("SSL") || t.getMessage().contains("protocol")))){
            ui.showErrorMessage("@feature.unsupported");
        }else if(error instanceof HttpStatusException st){
            ui.showErrorMessage(Core.bundle.format("connectfail", Strings.capitalize(st.status.toString().toLowerCase())));
        }else if(error.getMessage() != null && error.getMessage().toLowerCase(Locale.ROOT).contains("writable dex")){
            ui.showException("@error.moddex", error);
        }else{
            ui.showException(error);
        }
    }

    void getModList(Cons<Seq<ModListing>> listener){
        getModList(0, listener);
    }

    void getModList(int index, Cons<Seq<ModListing>> listener){
        if(index >= modJsonURLs.length) return;

        if(modList != null){
            listener.get(modList);
            return;
        }

        Http.get(modJsonURLs[index], response -> {
            String strResult = response.getResultAsString();

            Core.app.post(() -> {
                try{
                    modList = JsonIO.json.fromJson(Seq.class, ModListing.class, strResult);

                    var d = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                    Func<String, Date> parser = text -> {
                        try{
                            return d.parse(text);
                        }catch(Exception e){
                            return new Date();
                        }
                    };

                    modList.sortComparing(m -> parser.get(m.lastUpdated)).reverse();
                    listener.get(modList);
                }catch(Exception e){
                    Log.err(e);
                    ui.showException(e);
                }
            });
        }, error -> {
            if(index < modJsonURLs.length - 1){
                getModList(index + 1, listener);
            }else{
                Core.app.post(() -> {
                    modError(error);
                    if(browser != null){
                        browser.hide();
                    }
                });
=======
            if(updaterElement != null){
                updaterElement.remove();
                updaterElement = null;
>>>>>>> v159.3
            }
        });

        browser = new ModBrowserDialog();
    }

    public void refreshModUpdates(){
        ObjectMap<String, LoadedMod> repoToMod = new ObjectMap<>();
        for(var mod : mods.getMods()){
            String repo = mod.getRepo();
            if(!mod.hasSteamID() && repo != null){
                repoToMod.put(repo, mod);
            }
        }

        browser.getModList(list -> {
            withUpdates.clear();
            for(var entry : list){
                var mod = repoToMod.get(entry.repo);
                if(mod != null){
                    Log.info("FOUND: " + mod);
                    modToListing.put(mod, entry);
                    if(Strings.checkNewerSemver(entry.version, mod.meta.version)) withUpdates.add(mod);
                }
            }

            if(withUpdates.size > 0){
                setup();
            }
        });
    }

    public boolean hasUpdate(LoadedMod mod){
        return withUpdates.contains(mod);
    }

    void setup(){
        float h = 110f;
        float w = Math.min(Core.graphics.getWidth() / Scl.scl(1.05f) - Scl.scl(28f), 520f);

        cont.clear();
        cont.defaults().width(Math.min(Core.graphics.getWidth() / Scl.scl(1.05f), 556f)).pad(4);
        cont.add("@mod.reloadrequired").visible(mods::requiresReload).center().get().setAlignment(Align.center);
        cont.row();

        cont.table(buttons -> {
            buttons.left().defaults().growX().height(60f).uniformX();

            TextButtonStyle style = Styles.flatBordert;
            float margin = 12f;

            buttons.button("@mod.import", Icon.add, style, () -> {
                BaseDialog dialog = new BaseDialog("@mod.import");

                TextButtonStyle bstyle = Styles.flatt;

                dialog.cont.table(Tex.button, t -> {
                    t.defaults().size(300f, 70f);
                    t.margin(12f);

                    t.button("@mod.import.file", Icon.file, bstyle, () -> {
                        dialog.hide();

                        FileChooser.open("zip", "jar").submitMulti(files -> {
                            for(var file : files){
                                try{
                                    mods.importMod(file);
                                }catch(Exception e){
                                    ui.showException(e.getMessage() != null && e.getMessage().toLowerCase(Locale.ROOT).contains("writable dex") ? "@error.moddex" : "", e);
                                    Log.err(e);
                                }
                            }

                            setup();
                        });
                    }).margin(12f);

                    t.row();

                    t.button("@mod.import.github", Icon.github, bstyle, () -> {
                        dialog.hide();

                        ui.showTextInput("@mod.import.github", "", 64, Core.settings.getString("lastmod", ""), text -> {
                            //clean up the text in case somebody inputs a URL or adds random spaces
                            text = text.trim().replace(" ", "");
                            if(text.startsWith("https://github.com/")) text = text.substring("https://github.com/".length());

                            Core.settings.put("lastmod", text);
                            //there's no good way to know if it's a java mod here, so assume it's not
<<<<<<< HEAD
                            githubImportMod(text, false, null, null);
=======
                            githubImportMod(text, false, null, true);
>>>>>>> v159.3
                        });
                    }).margin(12f);
                });
                dialog.addCloseButton();

                dialog.show();

            }).margin(margin);

            buttons.button("@mods.browser", Icon.menu, style, () -> browser.show()).margin(margin);
        }).width(w);

        cont.row();

        if(!mods.list().isEmpty()){
            boolean[] anyDisabled = {false};
            Table[] pane = {null};

            Cons<String> rebuild = query -> {
                var cont = pane[0];
                cont.clear();
                boolean any = false;
                for(LoadedMod mod : mods.list()){
                    if(Strings.matches(query, mod.meta.displayName)){
                        any = true;
                        if(!mod.enabled() && !anyDisabled[0] && mods.list().size > 0){
                            anyDisabled[0] = true;
                            cont.row();
                            cont.image().growX().height(4f).pad(6f).color(Pal.gray).row();
                        }

                        cont.button(t -> {
                            t.top().left();
                            t.margin(12f);

                            String stateDetails = getStateDetails(mod);
                            if(stateDetails != null){
                                t.addListener(new Tooltip(f -> f.background(Styles.black8).margin(4f).add(stateDetails).growX().width(400f).wrap()));
                            }

                            t.defaults().left().top();
                            t.table(title1 -> {
                                title1.left();

                                title1.add(new BorderImage(){{
                                    if(mod.iconTexture != null){
                                        setDrawable(new TextureRegion(mod.iconTexture));
                                    }else{
                                        setDrawable(Tex.nomap);
                                    }
                                    border(Pal.accent);
                                }}).size(h - 8f).padTop(-8f).padLeft(-8f).padRight(8f);

                                title1.table(text -> {
                                    boolean hideDisabled = !mod.isSupported() || mod.hasUnmetDependencies() || mod.hasContentErrors();
                                    String shortDesc = mod.meta.shortDescription();

                                    text.add("[accent]" + Strings.stripColors(mod.meta.displayName) + "\n" +
                                        (shortDesc.length() > 0 ? "[lightgray]" + shortDesc + "\n" : "")
                                        //so does anybody care about version?
                                        //+ "[gray]v" + Strings.stripColors(trimText(item.meta.version)) + "\n"
                                        + (mod.enabled() || hideDisabled ? "" : Core.bundle.get("mod.disabled") + ""))
                                    .wrap().top().width(300f).growX().left();

                                    text.row();

                                    String state = getStateText(mod);
                                    if(state != null){
                                        text.labelWrap(state).growX().row();
                                    }
                                }).top().growX();

                                title1.add().growX();
                            }).growX().growY().left();

                            t.table(right -> {
                                right.right();
                                right.button(mod.enabled() ? Icon.downOpen : Icon.upOpen, Styles.clearNonei, () -> {
                                    mods.setEnabled(mod, !mod.enabled());
                                    setup();
                                }).size(50f).disabled(!mod.isSupported());

                                right.button(mod.hasSteamID() ? Icon.link : Icon.trash, Styles.clearNonei, () -> {
                                    if(!mod.hasSteamID()){
                                        ui.showConfirm("@confirm", "@mod.remove.confirm", () -> {
                                            mods.removeMod(mod);
                                            withUpdates.remove(mod);
                                            setup();
                                        });
                                    }else{
                                        platform.viewListing(mod);
                                    }
                                }).size(50f);

                                if(steam && !mod.hasSteamID()){
                                    right.row();
                                    right.button(Icon.export, Styles.clearNonei, () -> {
                                        platform.publish(mod);
                                    }).size(50f);
                                }
                            }).growX().right().padRight(-8f).padTop(-8f);
                        }, Styles.grayt, () -> showMod(mod)).size(w, h).growX().pad(4f).padTop(8f).row();

                        if(hasUpdate(mod)){
                            cont.button(b -> {
                                b.margin(6f);
                                b.left();
                                b.image(Icon.download).color(Color.lightGray).size(iconMed).padRight(8f);
                                var list = modToListing.get(mod);
                                b.add(Core.bundle.format("mods.update.available", list == null ? "<unknown>" : list.version));
                            }, Styles.grayt, () -> {
                                githubImportMod(mod.getRepo(), mod.isJava(), null, false);
                            }).width(w).height(48f).padTop(-4f).row();
                        }
                    }
                }

                if(!any){
                    cont.add("@none.found").color(Color.lightGray).pad(4);
                }
            };

            if(!mobile || Core.graphics.isPortrait()){
                cont.table(search -> {
                    search.image(Icon.zoom).padRight(8f);
                    search.field("", rebuild).growX();
                }).fillX().padBottom(4);
            }

            cont.row();
            cont.pane(table1 -> {
                pane[0] = table1.margin(10f).top();
                rebuild.get("");
            }).scrollX(false).update(s -> scroll = s.getScrollY()).get().setScrollYForce(scroll);

            cont.row();

            if(withUpdates.size > 1){
                cont.button(b -> {
                    b.margin(6f);
                    b.image(Icon.download).size(iconMed).padRight(8f);
                    b.add("@mods.update.all");
                    b.image(Icon.download).size(iconMed).padLeft(8f);
                }, Styles.grayt, () -> {
                    var queue = withUpdates.toSeq();

                    int[] index = {0};

                    //TODO: this is an awful hack that forces mods to be downloaded in sequence
                    //handling callbacks in the functions is extremely tedious, so this is the cleanest method I could find
                    cancelledImport = false;
                    if(updaterElement != null) updaterElement.remove();
                    updaterElement = new Element();

                    updaterElement.update(() -> {
                        if(index[0] >= queue.size || cancelledImport){
                            updaterElement.remove();
                        }else if(!ui.loadfrag.shown()){ //loading not shown, queue next one
                            var next = queue.get(index[0] ++);

                            githubImportMod(next.getRepo(), next.isJava(), null, false);
                        }
                    });
                    addChild(updaterElement);
                }).padTop(8f).width(w).height(60f).padTop(12f).row();
            }
        }else{
            cont.table(Styles.black6, t -> t.add("@mods.none")).height(80f);
        }

        cont.row();
    }

    private @Nullable String getStateText(LoadedMod item){
        if(item.isOutdated()){
            return "@mod.incompatiblemod";
        }else if(item.clientBlacklisted()){
            return "@client.mod.clientblacklisted";
        }else if(item.isBlacklisted()){
            return "@mod.blacklisted";
        }else if(!item.isSupported() || !Version.isAtLeast(item.meta.minGameVersion)){
            return "@mod.incompatiblegame";
        }else if(item.state == ModState.circularDependencies){
            return "@mod.circulardependencies";
        }else if(item.state == ModState.incompleteDependencies){
            return "@mod.incompletedependencies";
        }else if(item.hasUnmetDependencies()){
            return "@mod.unmetdependencies";
        }else if(item.hasContentErrors()){
            return "@mod.erroredcontent";
        }else if(item.meta.hidden){
            return "@mod.multiplayer.compatible";
        }
        return null;
    }

    private @Nullable String getStateDetails(LoadedMod item){
        if(item.isOutdated()){
            return "@mod.incompatiblemod.details";
        }else if(item.clientBlacklisted()){
            return "@client.mod.clientblacklisted.details";
        }else if(item.isBlacklisted()){
            return "@mod.blacklisted.details";
        }else if(!item.isSupported()){
            return Core.bundle.format("mod.requiresversion.details", item.meta.minGameVersion);
        }else if(item.state == ModState.circularDependencies){
            return "@mod.circulardependencies.details";
        }else if(item.state == ModState.incompleteDependencies){
            return Core.bundle.format("mod.incompletedependencies.details", item.missingDependencies.toString(", "));
        }else if(item.hasUnmetDependencies()){
            return Core.bundle.format("mod.missingdependencies.details", item.missingDependencies.toString(", "));
        }else if(item.hasContentErrors()){
            return "@mod.erroredcontent.details";
        }
        return null;
    }

    private void reload(){
        ui.showInfoOnHidden("@mods.reloadexit", () -> {
            ClientUtils.restartGame();
        });
    }

    private void showMod(LoadedMod mod){
        BaseDialog dialog = new BaseDialog(mod.meta.displayName);

        // Manually add a close button/listener and set the default height as we don't want to set a default width which addCloseButton() does
        dialog.buttons.defaults().height(64).minWidth(210);
        dialog.buttons.button("@back", Icon.left, dialog::hide).wrapLabel(false);
        dialog.addCloseListener();

        // Foo's client mod auto/mass update toggles
        dialog.buttons.table(Tex.button, t ->
            t.check("@client.mod.autoupdate", settings.getBool(mod.autoUpdateString(), true), b -> { if(b) settings.remove(mod.autoUpdateString()); else settings.put(mod.autoUpdateString(), false); }).fill().get().getLabelCell().fillX()
        );
        dialog.buttons.table(Tex.button, t ->
            t.check("@client.mod.massupdate", settings.getBool(mod.massUpdateString(), true), b -> { if(b) settings.remove(mod.massUpdateString()); else settings.put(mod.massUpdateString(), false); }).fill().padTop(4f).padBottom(4f).get().getLabelCell().fillX()
        );

        if(!mobile){
            dialog.buttons.button("@mods.openfolder", Icon.link, () -> Core.app.openFolder(mod.file.absolutePath())).wrapLabel(false);
        }

        if(mod.getRepo() != null){
            boolean showImport = !mod.hasSteamID();
            dialog.buttons.button("@mods.github.open", Icon.link, () -> Core.app.openURI("https://github.com/" + mod.getRepo())).wrapLabel(false);
            if(mobile && showImport) dialog.buttons.row();
<<<<<<< HEAD
            if(showImport) dialog.buttons.button("@mods.browser.reinstall", Icon.download, () -> githubImportMod(mod.getRepo(), mod.isJava(), null, null)).wrapLabel(false);
=======
            if(showImport) dialog.buttons.button("@mods.browser.reinstall", Icon.download, () -> githubImportMod(mod.getRepo(), mod.isJava(), null, false));
>>>>>>> v159.3
        }

        dialog.cont.pane(desc -> {
            desc.center();
            desc.defaults().padTop(10).left();

            desc.add("@editor.name").padRight(10).color(Color.gray).padTop(0);
            desc.row();
            desc.add(mod.meta.displayName).growX().wrap().padTop(2);
            desc.row();
            if(mod.meta.author != null){
                desc.add("@editor.author").padRight(10).color(Color.gray);
                desc.row();
                desc.add(mod.meta.author).growX().wrap().padTop(2);
                desc.row();
            }
            if(mod.meta.version != null){
                desc.add("@mod.version").padRight(10).color(Color.gray).top();
                desc.row();
                desc.add(mod.meta.version).growX().wrap().padTop(2);
                desc.row();
            }
            if(mod.meta.description != null){
                desc.add("@editor.description").padRight(10).color(Color.gray).top();
                desc.row();
                desc.add(mod.meta.description).growX().wrap().padTop(2);
                desc.row();
            }

            String state = getStateDetails(mod);

            if(state != null){
                desc.add("@mod.disabled").padTop(13f).padBottom(-6f).row();
                desc.add(state).growX().wrap().row();
            }

        }).width(400f);

        Seq<UnlockableContent> all = Seq.with(content.getContentMap()).<Content>flatten().select(c -> c.minfo.mod == mod && c instanceof UnlockableContent u && !u.isHidden()).as();
        if(all.any()){
            dialog.cont.row();
            dialog.cont.button("@mods.viewcontent", Icon.book, () -> {
                BaseDialog d = new BaseDialog(mod.meta.displayName);
                d.cont.pane(cs -> {
                    int i = 0;
                    for(UnlockableContent c : all){
                        cs.button(new TextureRegionDrawable(c.uiIcon), Styles.flati, iconMed, () -> {
                            ui.content.show(c);
                        }).size(50f).with(im -> {
                            var click = im.getClickListener();
                            im.update(() -> im.getImage().color.lerp(!click.isOver() ? Color.lightGray : Color.white, 0.4f * Time.delta));

                        }).tooltip(c.localizedName);

                        if(++i % (int)Math.min(Core.graphics.getWidth() / Scl.scl(110), 14) == 0) cs.row();
                    }
                }).grow();
                d.addCloseButton();
                d.show();
                currentContent = d;
            }).size(300, 50).pad(4);
        }

        dialog.show();
    }

<<<<<<< HEAD
    private void showModBrowser(){
        rebuildBrowser();
        browser.show();
    }

    private void rebuildBrowser(){
        ObjectSet<String> installed = mods.list().map(m -> m.getRepo()).asSet();

        browserTable.clear();
        browserTable.add("@loading");

        int cols = (int)Math.max(Core.graphics.getWidth() / Scl.scl(480), 1);

        getModList(0, rlistings -> {
            browserTable.clear();
            int i = 0;

            var listings = rlistings;
            if(!orderDate){
                listings = rlistings.copy();
                listings.sortComparing(m1 -> -m1.stars);
            }

            for(ModListing mod : listings){
                if(((mod.hasJava || mod.hasScripts && !mod.iosCompatible) && Vars.ios) ||
                    (!Strings.matches(searchtxt, mod.name) && !Strings.matches(searchtxt, mod.repo))
                ) continue;

                float s = 64f;

                browserTable.button(con -> {
                    con.margin(0f);
                    con.left();

                    String repo = mod.repo;
                    con.add(new BorderImage(){
                        TextureRegion last;

                        {
                            border(installed.contains(repo) ? Pal.accent : Color.lightGray);
                            setDrawable(Tex.nomap);
                            pad = Scl.scl(4f);
                        }

                        @Override
                        public void draw(){
                            super.draw();

                            //textures are only requested when the rendering happens; this assists with culling
                            if(!textureCache.containsKey(repo)){
                                textureCache.put(repo, last = Core.atlas.find("nomap"));
                                Http.get("https://raw.githubusercontent.com/Anuken/MindustryMods/master/icons/" + repo.replace("/", "_"), res -> {
                                    Pixmap pix = new Pixmap(res.getResult());
                                    Core.app.post(() -> {
                                        try{
                                            var tex = new Texture(pix);
                                            tex.setFilter(TextureFilter.linear);
                                            textureCache.put(repo, new TextureRegion(tex));
                                            pix.dispose();
                                        }catch(Exception e){
                                            Log.err(e);
                                        }
                                    });
                                }, err -> {});
                            }

                            var next = textureCache.get(repo);
                            if(last != next){
                                last = next;
                                setDrawable(next);
                            }
                        }
                    }).size(s).pad(4f * 2f);

                    String infoText =
                    "[accent]" + mod.name.replace("\n", "") +

                    (installed.contains(mod.repo) ? "\n[lightgray]" + Core.bundle.get("mod.installed") : "") +
                    "\n[lightgray]\uE809 " + mod.stars +
                    "\n" + Strings.truncate(mod.description, 30, "...") +
                    (!Version.isAtLeast(mod.minGameVersion) ? "\n" + Core.bundle.format("mod.requiresversion", mod.minGameVersion) :
                    ((mod.hasJava && Strings.parseDouble(mod.minGameVersion, 0) < minJavaModGameVersion && !mod.legacyCompatible) ? "\n" + Core.bundle.get("mod.incompatiblemod") : ""));

                    con.add(infoText).width(358f).wrap().grow().pad(4f, 2f, 4f, 6f).top().left().labelAlign(Align.topLeft);

                }, Styles.grayt, () -> {
                    var sel = new BaseDialog(mod.name);
                    sel.cont.pane(p -> p.add(mod.description + "\n\n[accent]" + Core.bundle.get("editor.author") + "[lightgray] " + mod.author)
                        .width(mobile ? 400f : 500f).wrap().pad(4f).labelAlign(Align.center, Align.left)).grow();
                    sel.buttons.defaults().size(150f, 54f).pad(2f);
                    sel.buttons.button("@back", Icon.left, () -> {
                        sel.clear();
                        sel.hide();
                    });

                    var found = mods.list().find(l -> mod.repo != null && mod.repo.equals(l.getRepo()));
                    sel.buttons.button(found == null ? "@mods.browser.add" : "@mods.browser.reinstall", Icon.download, () -> {
                        sel.hide();
                        githubImportMod(mod.repo, mod.hasJava, null, null);
                    });

                    if(Core.graphics.isPortrait()){
                        sel.buttons.row();
                    }

                    sel.buttons.button("@mods.github.open", Icon.link, () -> {
                        Core.app.openURI("https://github.com/" + mod.repo);
                    });

                    sel.buttons.button("@mods.browser.view-releases", Icon.zoom, () -> {
                        BaseDialog load = new BaseDialog("");
                        load.cont.add("[accent]" + Core.bundle.get("mods.browser.fetching"));
                        load.show();
                        Http.get(ghApi + "/repos/" + mod.repo + "/releases", res -> {
                            var json = Jval.read(res.getResultAsString());
                            JsonArray releases = json.asArray();

                            Core.app.post(() -> {
                                load.hide();

                                if(releases.size == 0){
                                    ui.showInfo("@mods.browser.noreleases");
                                }else{
                                    sel.hide();
                                    var downloads = new BaseDialog("@mods.browser.releases");
                                    downloads.cont.pane(p -> {
                                        for(int j = 0; j < releases.size; j++){
                                            var release = releases.get(j);

                                            int index = j;
                                            p.table(((TextureRegionDrawable)Tex.whiteui).tint(Pal.darkestGray), t -> {
                                                t.add("[accent]" + release.getString("name") + (index == 0 ? " " + Core.bundle.get("mods.browser.latest") : "")).top().left().growX().wrap().pad(5f);
                                                t.row();
                                                t.add((release.getString("published_at")).substring(0, 10).replaceAll("-", "/")).top().left().growX().wrap().pad(5f).color(Color.gray);
                                                t.row();
                                                t.table(b -> {
                                                    b.defaults().size(150f, 54f).pad(2f);
                                                    b.button("@mods.github.open-release", Icon.link, () -> Core.app.openURI(release.getString("html_url")));
                                                    b.button("@mods.browser.add", Icon.download, () -> {
                                                        String releaseUrl = release.getString("url");
                                                        githubImportMod(mod.repo, mod.hasJava, releaseUrl.substring(releaseUrl.lastIndexOf("/") + 1));
                                                    });
                                                }).right();
                                            }).margin(5f).growX().pad(5f);

                                            if(j < releases.size - 1) p.row();
                                        }
                                    }).width(500f).scrollX(false).fillY();
                                    downloads.buttons.button("@back", Icon.left, () -> {
                                        downloads.clear();
                                        downloads.hide();
                                        sel.show();
                                    }).size(150f, 54f).pad(2f);
                                    downloads.keyDown(KeyCode.escape, downloads::hide);
                                    downloads.keyDown(KeyCode.back, downloads::hide);
                                    downloads.hidden(sel::show);
                                    downloads.show();
                                }
                            });
                        }, t -> Core.app.post(() -> {
                            modError(t);
                            load.hide();
                        }));
                    });
                    sel.keyDown(KeyCode.escape, sel::hide);
                    sel.keyDown(KeyCode.back, sel::hide);
                    sel.show();
                }).width(438f).pad(4).growX().left().height(s + 8*2f).fillY();

                if(++i % cols == 0) browserTable.row();
            }
        });
    }

    private void handleMod(String repo, HttpResponse result, @Nullable String prevVersion){
         try{
=======
    protected void handleMod(String repo, HttpResponse result, boolean forceEnable){
        try{
>>>>>>> v159.3
            Fi file = tmpDirectory.child(repo.replace("/", "") + ".zip");
            long len = result.getContentLength();
            Floatc cons = len <= 0 ? f -> {} : p -> modImportProgress = p;

            try(var stream = file.write(false)){
                Streams.copyProgress(result.getResultAsStream(), stream, len, 4096, p -> {
                    if(cancelledImport) throw new RuntimeException("cancelled");
                    cons.get(p);
                });
            }

            if(cancelledImport) return;

<<<<<<< HEAD
            Fi zip = file.isDirectory() ? file : new ZipFi(file);
            if(OS.isMac) zip.child(".DS_Store").delete(); //macOS loves adding garbage files that break everything
            if(zip.list().length == 1 && zip.list()[0].isDirectory()) zip = zip.list()[0]; // FINISHME: This should be a method in the ZipFi class as its used thrice and the current impl is awful as it calls list thrice for no reason
            ModMeta meta = mods.findMeta(zip); // The three lines above are needed so that this can work as it won't find the meta file when passing it a zip as a normal file

            if(meta == null) Log.warn("Mod @ doesn't have a '[mod/plugin].[h]json' file, skipping.", file);

            if (meta == null || meta.version == null || !meta.version.equals(prevVersion)) {
                var mod = mods.importMod(file);
                mod.setRepo(repo);
            }

=======
            var mod = mods.importMod(file, forceEnable);
            mod.setRepo(repo);
>>>>>>> v159.3
            file.delete();
            Core.app.post(() -> {
                var same = withUpdates.toSeq().find(l -> Structs.eq(l.getRepo(), repo));
                if(same != null) withUpdates.remove(same);

                try{
                    setup();
                    ui.loadfrag.hide();
                }catch(Throwable e){
                    ui.showException(e);
                }
            });
        }catch(Throwable e){
            if(cancelledImport) return;
            showModError(e);
        }

        importSuccess(repo);
    }

    private void importSuccess(String repo){
        var func = onSuccess.remove(repo);
        if(func == null) return;
        Core.app.post(func);
    }

<<<<<<< HEAD
    private void importFail(Throwable t){
        if (!autoUpdating) Core.app.post(() -> modError(t));
        else Log.err("Mod Auto Update Error", t);
=======
    protected void importFail(Throwable t){
        Core.app.post(() -> showModError(t));
>>>>>>> v159.3
    }

    public void showModError(Throwable error){
        ui.loadfrag.hide();

        if(error instanceof NoSuchMethodError || Strings.getCauses(error).contains(t -> t.getMessage() != null && (t.getMessage().contains("trust anchor") || t.getMessage().contains("SSL") || t.getMessage().contains("protocol")))){
            ui.showErrorMessage("@feature.unsupported");
        }else if(error instanceof HttpStatusException st){
            ui.showErrorMessage(Core.bundle.format("connectfail", Strings.capitalize(st.status.toString().toLowerCase())));
        }else if(error.getMessage() != null && error.getMessage().toLowerCase(Locale.ROOT).contains("writable dex")){
            ui.showException("@error.moddex", error);
        }else{
            ui.showException(error);
        }
    }

<<<<<<< HEAD
    private void githubImportMod(String repo, boolean isJava, @Nullable String release){
        githubImportMod(repo, isJava, release, null);
    }

    public void githubImportMod(String repo, boolean isJava, @Nullable String release, @Nullable String prevVersion, Runnable onSuccessRunnable){
        onSuccess.put(repo, onSuccessRunnable);
        githubImportMod(repo, isJava, release, prevVersion);
    }

    public void githubImportMod(String repo, boolean isJava, @Nullable String release, @Nullable String prevVersion){
        modImportProgress = 0f;
        cancelledImport = false;
        if(prevVersion == null) ui.loadfrag.show("@downloading");
=======
    public void githubImportMod(String repo, boolean isJava, boolean forceEnable){
        githubImportMod(repo, isJava, null, forceEnable);
    }

    public void githubImportMod(String repo, boolean isJava, @Nullable String release, boolean forceEnable){
        modImportProgress = 0f;
        cancelledImport = false;
        ui.loadfrag.show(Core.bundle.format("mods.downloading", repo));
>>>>>>> v159.3
        ui.loadfrag.setProgress(() -> modImportProgress);
        ui.loadfrag.setButton(() -> {
            ui.loadfrag.hide();
            cancelledImport = true;
        });

        if(isJava){
<<<<<<< HEAD
            githubImportJavaMod(repo, release, prevVersion);
=======
            githubImportJavaMod(repo, release, forceEnable);
>>>>>>> v159.3
        }else{
            Http.get(ghApi + "/repos/" + repo, res -> {
                if(cancelledImport) return;
                var json = Jval.read(res.getResultAsString());
                String mainBranch = json.getString("default_branch");
                String language = json.getString("language", "<none>");

                //this is a crude heuristic for class mods; only required for direct github import
                //TODO make a more reliable way to distinguish java mod repos
                if(language.equals("Java") || language.equals("Kotlin") || language.equals("Groovy") || language.equals("Scala")){
<<<<<<< HEAD
                    githubImportJavaMod(repo, release, prevVersion);
                }else{
                    githubImportBranch(mainBranch, repo, release, prevVersion);
=======
                    githubImportJavaMod(repo, release, forceEnable);
                }else{
                    githubImportBranch(mainBranch, repo, release, forceEnable);
>>>>>>> v159.3
                }
            }, this::importFail);
        }
    }

<<<<<<< HEAD
    public void importDependencies(Seq<String> dependencies, Runnable done){
        getModList(listings -> {
            listings.each(l -> dependencies.contains(l.internalName), l -> {
                dependencies.remove(l.internalName);
                githubImportMod(l.repo, l.hasJava);
            });
            done.run();
        });
    }

    private void githubImportJavaMod(String repo, @Nullable String release, @Nullable String prevVersion){
=======
    public void githubImportJavaMod(String repo, @Nullable String release, boolean forceEnable){
>>>>>>> v159.3
        //grab latest release
        Http.get(ghApi + "/repos/" + repo + "/releases/" + (release == null ? "latest" : release), res -> {
            if(cancelledImport) return;
            var json = Jval.read(res.getResultAsString());
            var assets = json.get("assets").asArray();

            //prioritize dexed jar, as that's what Sonnicon's mod template outputs
            var dexedAsset = assets.find(j -> j.getString("name").startsWith("dexed") && j.getString("name").endsWith(".jar"));
            var asset = dexedAsset == null ? assets.find(j -> j.getString("name").endsWith(".jar")) : dexedAsset;

            if(asset != null){
                //grab actual file
                var url = asset.getString("browser_download_url");

                Http.get(url, result -> {
                    if(cancelledImport) return;
<<<<<<< HEAD
                    handleMod(repo, result, prevVersion);
=======
                    handleMod(repo, result, forceEnable);
>>>>>>> v159.3
                }, this::importFail);
            }else{
                throw new ArcRuntimeException("No JAR file found in releases. Make sure you have a valid jar file in the mod's latest Github Release.");
            }
        }, this::importFail);
    }

<<<<<<< HEAD
    private void githubImportBranch(String branch, String repo, @Nullable String release, @Nullable String prevVersion){
=======
    public void githubImportBranch(String branch, String repo, @Nullable String release, boolean forceEnable){
>>>>>>> v159.3
        if(release != null) {
            Http.get(ghApi + "/repos/" + repo + "/releases/" + release, res -> {
                if(cancelledImport) return;
                String zipUrl = Jval.read(res.getResultAsString()).getString("zipball_url");
                Http.get(zipUrl, loc -> {
                    if(cancelledImport) return;
                    if(loc.getHeader("Location") != null){
                        Http.get(loc.getHeader("Location"), result -> {
                            handleMod(repo, result, prevVersion);
                            if(cancelledImport) return;
<<<<<<< HEAD
                        }, this::importFail);
                    }else{
                        handleMod(repo, loc, prevVersion);
=======
                            handleMod(repo, result, forceEnable);
                        }, this::importFail);
                    }else{
                        handleMod(repo, loc, forceEnable);
>>>>>>> v159.3
                    }
                }, this::importFail);
            });
        }else{
            Http.get(ghApi + "/repos/" + repo + "/zipball/" + branch, loc -> {
                if(cancelledImport) return;
                if(loc.getHeader("Location") != null){
                    Http.get(loc.getHeader("Location"), result -> {
                        if(cancelledImport) return;
<<<<<<< HEAD
                        handleMod(repo, result, prevVersion);
                    }, this::importFail);
                }else{
                    handleMod(repo, loc, prevVersion);
=======
                        handleMod(repo, result, forceEnable);
                    }, this::importFail);
                }else{
                    handleMod(repo, loc, forceEnable);
>>>>>>> v159.3
                }
            }, this::importFail);
        }
    }
}
