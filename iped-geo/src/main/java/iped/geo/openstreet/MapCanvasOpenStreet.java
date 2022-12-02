package iped.geo.openstreet;

import java.awt.Color;
import java.awt.Component;
import java.io.IOException;
import java.net.URL;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.Semaphore;

import javax.swing.UIManager;

import org.apache.commons.io.IOUtils;

import iped.geo.AbstractMapCanvas;
import iped.utils.UiUtil;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker.State;
import javafx.embed.swing.JFXPanel;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebErrorEvent;
import javafx.scene.web.WebEvent;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

public class MapCanvasOpenStreet extends AbstractMapCanvas {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    WebView browser;
    WebEngine webEngine = null;
    final JFXPanel jfxPanel;
    JSInterfaceFunctionsOpenStreet jsInterface = new JSInterfaceFunctionsOpenStreet(this);

    boolean dragging = false;
    double dragStartX, dragStartY;
    ChangeListener<State> onLoadChange;

    String url;
    private boolean htmlloaded;

    public MapCanvasOpenStreet() {
        this.jfxPanel = new JFXPanel();

        Platform.runLater(new Runnable() {
            public void run() {
                browser = new WebView();
                jfxPanel.setScene(new Scene(browser));

                browser.setOnMouseReleased(e -> {
                    dragging = false;
                });

                browser.setOnMouseDragged(e -> {

                    if (dragging) {
                        final int x = (int) Math.ceil(dragStartX - e.getX());
                        final int y = (int) Math.ceil(dragStartY - e.getY());

                        try {
                            webEngine.executeScript("map.panBy({x:" + x + ", y:" + y
                                    + "},{duration: 1,easeLinearity: 1,noMoveStart: true,animate: false})");
                        } catch (Exception e1) {
                            e1.printStackTrace();
                        }
                    }

                    dragging = true;
                    dragStartX = e.getX();
                    dragStartY = e.getY();

                });

                webEngine = browser.getEngine();
                webEngine.setJavaScriptEnabled(true);
                webEngine.setOnError(new EventHandler<WebErrorEvent>() {
                    public void handle(WebErrorEvent event) {
                        System.out.println("Error:" + event.getMessage()); //$NON-NLS-1$
                    }
                });
                webEngine.setOnAlert(new EventHandler<WebEvent<String>>() {
                    public void handle(WebEvent<String> event) {
                        System.out.println("Alert:" + event.getData()); //$NON-NLS-1$
                    }
                });

                webEngine.loadContent(UiUtil.getUIEmptyHtml());

                onLoadChange = new ChangeListener<State>() {
                    @Override
                    public void changed(ObservableValue<? extends State> observable, State oldState, State newState) {
                        if (newState == State.SUCCEEDED) {
                            JSObject window = (JSObject) webEngine.executeScript("window"); //$NON-NLS-1$
                            window.setMember("app", jsInterface); //$NON-NLS-1$
                            try {
                                if (onLoadRunnables.size() > 0) {
                                    for (Iterator iterator = onLoadRunnables.iterator(); iterator.hasNext();) {
                                        Runnable runnable = (Runnable) iterator.next();
                                        runnable.run();
                                    }
                                    onLoadRunnables.clear();
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                };
            }
        });
    }

    @Override
    public void connect() {
        if (webEngine == null) {
            browser = new WebView();
            jfxPanel.setScene(new Scene(browser));
            webEngine = browser.getEngine();
        }

    }

    @Override
    public void disconnect() {
        jfxPanel.setScene(null);
        webEngine = null;
        browser = null;
    }

    @Override
    public void setText(final String html) {
        Platform.runLater(new Runnable() {
            public void run() {            	
                webEngine.getLoadWorker().stateProperty().removeListener(onLoadChange);
                webEngine.getLoadWorker().stateProperty().addListener(onLoadChange);
                runAfterLoad(new Runnable() {
                    @Override
                    public void run() {
                        updateUI();
                    }
                });
                webEngine.loadContent(html);
                jfxPanel.invalidate();
            }
        });
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public void load() {
        try {
            Semaphore sem = new Semaphore(1);
            sem.acquire();
            String html = getMainHtml();
            runAfterLoad(new Runnable() {
                @Override
                public void run() {
                    sem.release();
                }
            });
            setText(html);
            sem.acquire();
            sem.release();
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getMainHtml() throws IOException {
        String html = IOUtils.toString(getClass().getResourceAsStream("main.html"), "UTF-8"); //$NON-NLS-1$ //$NON-NLS-2$
        String js = IOUtils.toString(getClass().getResourceAsStream("L.KML.js"), "UTF-8"); //$NON-NLS-1$ //$NON-NLS-2$
        String markerclusterjs = IOUtils.toString(getClass().getResourceAsStream("leaflet.markercluster.js"), //$NON-NLS-1$
                "UTF-8"); //$NON-NLS-1$

        String layers_img = "data:image/png;base64," + Base64.getEncoder() //$NON-NLS-1$
                .encodeToString(IOUtils.toByteArray(getClass().getResourceAsStream("layers.png"))); //$NON-NLS-1$

        html = html.replace("{{layers_img}}", layers_img);            
        html = html.replace("{{markerclusterjs}}", markerclusterjs);
        
        String themeScript="applyLightTheme();";
        Color bgColor = UIManager.getLookAndFeelDefaults().getColor("Viewer.background");
        if(bgColor!=null) {
            themeScript="applyDarkTheme();";
        }
        html = html.replace("{{applyTheme}}", themeScript);
        
        html = html.replace("{{tileServerUrl}}", url);
        html = html.replace("{{toolbar}}", getToolBarHtml());

        html = html.replace("{{L.KML}}", js); //$NON-NLS-1$
        return html;
    }
    
    @Override
    public void setKML(String kml) {
        try {
            String html = getMainHtml();

            String b64_selecionado = "data:image/png;base64," + Base64.getEncoder() //$NON-NLS-1$
                    .encodeToString(IOUtils.toByteArray(getClass().getResourceAsStream("marcador_selecionado.png"))); //$NON-NLS-1$
            String b64_selecionado_m = "data:image/png;base64," + Base64.getEncoder() //$NON-NLS-1$
                    .encodeToString(IOUtils.toByteArray(getClass().getResourceAsStream("marcador_selecionado_m.png"))); //$NON-NLS-1$
            String b64_normal = "data:image/png;base64," + Base64.getEncoder() //$NON-NLS-1$
                    .encodeToString(IOUtils.toByteArray(getClass().getResourceAsStream("marcador_normal.png"))); //$NON-NLS-1$
            String b64_marcado = "data:image/png;base64," + Base64.getEncoder() //$NON-NLS-1$
                    .encodeToString(IOUtils.toByteArray(getClass().getResourceAsStream("marcador_marcado.png"))); //$NON-NLS-1$
            
                       
            kml = kml.replace("\n", "").replace("\r", "");
            kml = kml.replace("<Document>",
                    "<Document><Style id=\"item\"><IconStyle><Icon><href>{{icone_base64}}</href></Icon></IconStyle></Style>");
            kml = kml.replace("<Document>",
                    "<Document><Style id=\"itemSelecionado\"><IconStyle><Icon><href>{{b64_selecionado}}</href></Icon></IconStyle></Style>");
            kml = kml.replace("<Document>",
                    "<Document><Style id=\"itemSelecionadoMarcado\"><IconStyle><Icon><href>{{b64_selecionado_m}}</href></Icon></IconStyle></Style>");
            kml = kml.replace("<Document>",
                    "<Document><Style id=\"itemMarcado\"><IconStyle><Icon><href>{{b64_marcado}}</href></Icon></IconStyle></Style>");
            kml = kml.replace("{{icone_base64}}", b64_normal);
            kml = kml.replace("{{b64_selecionado}}", b64_selecionado);
            kml = kml.replace("{{b64_selecionado_m}}", b64_selecionado_m);
            kml = kml.replace("{{b64_marcado}}", b64_marcado);
            kml = kml.replace("</Placemark>", "<styleUrl>#item</styleUrl></Placemark>");
            
            setText(html);
            
            final String kmlFinal = kml;

            if(!htmlloaded) {
                htmlloaded=true;
                runAfterLoad(new Runnable() {
                    @Override
                    public void run() {
                        webEngine.executeScript("loadKml('"+kmlFinal+"')");
                    }
                });
            }else {
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        webEngine.executeScript("loadKml('"+kmlFinal+"')");
                    }
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void addSaveKmlFunction(Runnable save) {
        this.saveRunnable = save;
    }

    @Override
    public boolean isConnected() {
        return (webEngine != null);
    }

    @Override
    public Component getContainer() {
        return jfxPanel;
    }

    @Override
    public void update() {
        MapCanvasOpenStreet self = this;
        if (self.selectionMapToApply != null) {
            // repinta selecoes alteradas
            final String[] marks = new String[self.selectionMapToApply.keySet().size()];
            self.selectionMapToApply.keySet().toArray(marks);
            final HashMap<String, Boolean> selecoesAfazerCopy = selectionMapToApply;

            Platform.runLater(new Runnable() {
                public void run() {
                    boolean marcadorselecionado = false;
                    for (int i = 0; i < marks.length; i++) {
                        Boolean b = selecoesAfazerCopy.get(marks[i]);
                        if (b) {
                            marcadorselecionado = true;
                        }
                        try {
                            webEngine.executeScript("track.selecionaMarcador([\"" + marks[i] + "\"],'" + b + "');"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    if (marcadorselecionado) {
                        try {
                            webEngine.executeScript("track.centralizaSelecao();");
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
            self.selectionMapToApply = null;
        }
        if (self.leadSelectionToApply != null) {
            final String leadSelectionToApplyCopy = self.leadSelectionToApply;
            Platform.runLater(new Runnable() {
                public void run() {
                    try {
                        webEngine.executeScript("updateLeadMarker(\""+leadSelectionToApplyCopy.toString()+"\");");
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        // nothing
                    }
                }
            });
        }
        updateUI();
    }
    
    public void updateUI() {
        String themeScript="applyLightTheme();";
        Color bgColor = UIManager.getLookAndFeelDefaults().getColor("Viewer.background");
        if(bgColor!=null) {
            themeScript="applyDarkTheme();";
        }
        
        final String themeScriptFinal=themeScript; 
        Platform.runLater(new Runnable() {
            public void run() {
                try {
                    webEngine.executeScript(themeScriptFinal);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    // nothing
                }
            }
        });
    }

    @Override
    public void selectCheckbox(final String mid, final boolean b) {
        Platform.runLater(new Runnable() {
            public void run() {
                try {
                    webEngine.executeScript("track.marca(\"" + mid + "\",'" + b + "');"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void viewAll() {
        Platform.runLater(new Runnable() {
            public void run() {
                try {
                    webEngine.executeScript("track.fire('loaded');track.viewAll();");
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    // nothing
                }
            }
        });
    }
    
    @Override
    public void addPlacemark(String gid, String longit, String lat) {
        Platform.runLater(new Runnable() {
            public void run() {
                try {
                    webEngine.executeScript("track.addPlacemark('"+gid+"','"+lat+"','"+longit+"')");
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    // nothing
                }
            }
        });
    }
}
