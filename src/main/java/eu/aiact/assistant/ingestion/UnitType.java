package eu.aiact.assistant.ingestion;

/** Kinds of legal text. Articles and annexes are binding; recitals explain intent but are not. */
public enum UnitType {
    ARTICLE, RECITAL, ANNEX;

    public boolean binding() {
        return this != RECITAL;
    }
}
