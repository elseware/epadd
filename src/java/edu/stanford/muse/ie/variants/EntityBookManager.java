package edu.stanford.muse.ie.variants;

import edu.stanford.muse.AddressBookManager.AddressBook;
import edu.stanford.muse.index.Archive;
import edu.stanford.muse.index.Document;
import edu.stanford.muse.ner.Entity;
import edu.stanford.muse.ner.model.NEType;
import edu.stanford.muse.util.Pair;
import edu.stanford.muse.util.Span;
import edu.stanford.muse.util.Util;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.poi.util.ArrayUtil;

import javax.print.Doc;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/*
Since v7 we keep one entitybook per entity. Earlier we had one entity book to keep the information about all entity types.
With this change, we now introduce this class that contains information about different entitybooks, one per entity type.
 */
public class EntityBookManager {
    //we don't need to serialize this class as the date here will be made persistent in form of human readable files.
    public static long serialVersionUID = 1L;
    public static Log log = LogFactory.getLog(EntityBookManager.class);

    private Archive mArchive = null;

    public EntityBookManager(Archive archive){
        this.mArchive=archive;
    }
    //variable to hold mapping of different entity books, one per entity type.
    private Map<Short,EntityBook> mTypeToEntityBook = new LinkedHashMap<>();


    /**
     * This method returns the entitybook for the given type. If it is present in the map then the entitybook is returned from there. Otherwise the entitybook is created using lucene
     * index. So the idea is, first time archives of this modification will create entitybooks from lucene without any need of migration across versions.
     * NOTE: Never call get from mTypeToEntityBook map. Always use this method to get the entitybook for a given type. It ensures that if it was not filled initially (for whatever reasons)
     * it gets filled from lucene and then get returned.
     * @param entityType
     * @return
     */
    public EntityBook getEntityBookForType(Short entityType){
        if(mTypeToEntityBook.get(entityType)!=null)
            return mTypeToEntityBook.get(entityType);
        else{
            fillEntityBookFromLucene(entityType);
            return mTypeToEntityBook.get(entityType);
        }
    }
    /*
    Method to read different entity books from files and fill this object. This object is then returned to the caller. If file is not present that particular object is not
    constructed. It is not a problem because later when getEntityBook method is called (of this class) and no entitybook is found then an entitybook object is constructed for that type
    using lucene index.
     */
    public static EntityBookManager readObjectFromFiles(Archive archive, String entitybooksdirpath) {
        //here entitybooksdirpath is of the form             String dir = baseDir + File.separatorChar + Archive.BAG_DATA_FOLDER + File.separatorChar + Archive.ENTITYBOOKMANAGER_SUFFIX;

        EntityBookManager entityBookManager = new EntityBookManager(archive);
        if(!new File(entitybooksdirpath).exists())//if no directory exists then create it.
            new File(entitybooksdirpath).mkdir();

        //for each entity type get corresponding subidrectory inside dirpath. Open a file named entitybook in that subdirectory, read it, initialize entitybook and put it in the map.
        for(NEType.Type t: NEType.Type.values()) {
            String subdirname = t.getDisplayName();
            //open directory and look for entitybook filename inside it.
            String entitybookpath = entitybooksdirpath+File.separator+subdirname;
            String entitybookfile = entitybookpath+ File.separator+ Archive.ENTITYBOOK_SUFFIX;
            if(!new File(entitybookpath).exists())//if no directory exists then create it.
                new File(entitybookpath).mkdir();

            //if entitybook file exists then read it and fill entitybook object else fill this object from lucene index.
            if(new File(entitybookfile).exists()) {
                BufferedReader reader = null;
                try {
                    reader = new BufferedReader(new FileReader(entitybookfile));

                    StringBuilder stringBuilder = new StringBuilder();
                    String line = null;
                    while ((line = reader.readLine()) != null) {
                        stringBuilder.append(line);
                        stringBuilder.append("\r\n");
                    }
                    entityBookManager.fillEntityBookFromText(stringBuilder.toString(), t.getCode(), true);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }else{
                //fill it from lucene.
                EntityBook ebook = new EntityBook(t.getCode());
                entityBookManager.mTypeToEntityBook.put(t.getCode(),ebook);
                entityBookManager.fillEntityBookFromLucene(t.getCode());
            }

        }

        return entityBookManager;
    }

    /*
    Return a set of documents containing entities of the given type
     */
    public synchronized Collection<Document> getDocsWithEntityType(short type) {
        EntityBook ebook = this.getEntityBookForType(type);
        Set<Document> result = new LinkedHashSet<>();
        ebook.summary_L1_entityCountMap.values().stream().forEach(s->{
            result.addAll(s.messages);
        });
        return result;
    }

    /*
    Return the set of entitytypes actually present in this archive.
     */
    public synchronized Set<NEType.Type> getPresentEntityTypesInArchive() {
        Set<NEType.Type> result= new LinkedHashSet<>();
        for(NEType.Type t: NEType.Type.values()) {
            EntityBook ebook = this.getEntityBookForType(t.getCode());
            if(ebook.summary_L1_entityCountMap.values().size()>0)//add this type only if at least one entity of this type is found.
                result.add(t);
        }
        return result;

    }
        public  Map<Short,Integer> getEntitiesCountMapModuloThreshold(double threshold) {

        //iterate over all entitybooks and get their entitycount map modulo threshold.
        Map<Short,Integer> result= new LinkedHashMap<>();
        for(NEType.Type t: NEType.Type.values()) {
            EntityBook ebook = this.getEntityBookForType(t.getCode());
            result.put(t.getCode(),ebook.getEntitiesCountMapModuloThreshold(threshold));
        }
        return result;
    }
    /*
    Method for saving entity books to corresponding files.
     *//*
    public void writeObjectToFiles(String entitybooksdirpath) throws IOException {

        //here entitybooksdirpath is of the form             String dir = baseDir + File.separatorChar + Archive.BAG_DATA_FOLDER +File.separatorChar + Archive.SESSIONS_SUBDIR+ File.separatorChar + Archive.ENTITYBOOKS_SUFFIX;
        new File(entitybooksdirpath).mkdir();//create entitybook directory if not exists.
        for(NEType.Type t: NEType.Type.values()) {
            writeObjectToFile(entitybooksdirpath,t.getCode());
        }

    }*/
    /*
    Method for saving a particular entity book to corresponding file.
     */
    public void writeObjectToFile(String entitybooksdirpath, short entityType) throws IOException {
        //get corresponding entitybook for this type.
        String entitysubdirname = NEType.getTypeForCode(entityType).getDisplayName();
        new File(entitybooksdirpath + File.separator + entitysubdirname + File.separator).mkdir();//create directory if not exists.
        String entityBookPath = entitybooksdirpath + File.separator + entitysubdirname + File.separator + Archive.ENTITYBOOK_SUFFIX;
        EntityBook ebook = this.getEntityBookForType(entityType);
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(entityBookPath));
            ebook.writeObjectToStream(bw);
            bw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /* body = true => in message body, false => in subject */

    /**
     * This method is a wrapper over @getEntitiesInDocFromLucene. It reads the entities form lucene and filter out those which are not present
     * in the entitybooks. This is to make sure that entitybook remain as a single point of deciding if an entity is present in the doc or not.
     * @param document
     * @param body
     * @return
     */
    public Span[] getEntitiesInDoc(Document document, boolean body){
        Span[] names = getEntitiesInDocFromLucene(document,body);
        Set<Span> res=new LinkedHashSet<>();
        for(NEType.Type t: NEType.Type.values()) {
            EntityBook ebook = this.getEntityBookForType(t.getCode());
            for(Span name:names){
                if(ebook.nameToMappedEntity.get(EntityBook.canonicalize(name.text))!=null)
                    res.add(name);
            }
        }
        //return res as array
        return res.toArray(new Span[res.size()]);
    }


    /**
     * Get num top entities in the archive. This rating is by count.
     * @param num
     * @return
     */
    public Map<String,Collection<Document>> getDocsOfTopEntitiesByCount(int num){
        final Map<String,Set<Document>> topofeach = new LinkedHashMap<>();
        Map<String,Collection<Document>> topofall = new LinkedHashMap<>();
        for(NEType.Type t: NEType.Type.values()) {
            EntityBook ebook = this.getEntityBookForType(t.getCode());
            ebook.summary_L1_entityCountMap.entrySet().stream().sorted(new Comparator<Map.Entry<MappedEntity, Summary_L1>>() {
                @Override
                public int compare(Map.Entry<MappedEntity, Summary_L1> o1, Map.Entry<MappedEntity, Summary_L1> o2) {
                    return Integer.compare(o2.getValue().messages.size(), o1.getValue().messages.size());//we need ordering from high to low.
                }
            }).sorted(new Comparator<Map.Entry<MappedEntity, Summary_L1>>() {
                @Override
                public int compare(Map.Entry<MappedEntity, Summary_L1> o1, Map.Entry<MappedEntity, Summary_L1> o2) {
                    return Double.compare(o2.getValue().score, o1.getValue().score);
                }
            }).limit(num).forEach(e->{
                topofeach.put(e.getKey().getDisplayName(),e.getValue().messages);
            });
        }
        //now sort topofall and limit the result to top num entries. Return the result.
        topofall=topofeach.entrySet().stream().sorted(new Comparator<Map.Entry<String, Set<Document>>>() {
            @Override
            public int compare(Map.Entry<String, Set<Document>> o1, Map.Entry<String, Set<Document>> o2) {
                return Integer.compare(o2.getValue().size(),o1.getValue().size());//sort based on size.. largest to smallest.
            }
        }).limit(num).collect(Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue));
        return topofall;
    }

    public Set<Document> getDocsForEntities(Set<String> entities){
        Set<Document> docset = new LinkedHashSet<>();
        for(NEType.Type t: NEType.Type.values()) {
            EntityBook ebook = this.getEntityBookForType(t.getCode());
            for(String name:entities){
                MappedEntity me = ebook.nameToMappedEntity.get(EntityBook.canonicalize(name));
                if(me!=null){
                    //get set of docs for this one. and add to docset result.
                    docset.addAll(ebook.summary_L1_entityCountMap.get(me).messages);
                }
            }
        }
        return docset;
    }

    /* body = true => in message body, false => in subject */
    public Span[] getEntitiesInDocFromLucene(Document d, boolean body){
        try {
            return edu.stanford.muse.ner.NER.getNames(d, body, mArchive);
        }catch(Exception e) {
            Util.print_exception(e, log);
            return new Span[]{};
        }
    }

    public void fillEntityBookFromLucene( Short type){
        EntityBook ebook = new EntityBook(type);
        mTypeToEntityBook.put(type,ebook);

        double theta = 0.001;
        //docset map maps a mappedentity to it's score and the set of documents.
        Map<MappedEntity, Pair<Double,Set<Document>>> docsetmap = new LinkedHashMap<>();
        for (Document doc : mArchive.getAllDocs())

        {
            Span[] spansbody = getEntitiesInDocFromLucene(doc, true);
            Span[] spans = getEntitiesInDocFromLucene(doc, false);
            Span[] allspans = ArrayUtils.addAll(spans,spansbody);
            Set<String> seenInThisDoc = new LinkedHashSet<>();

            for (Span span : allspans) {
                // bail out if not of entity type that we're looking for, or not enough confidence
                if (span.type != type || span.typeScore < theta)
                    continue;

                String name = span.getText();
                String canonicalizedname = EntityBook.canonicalize(name);
                Double score = new Double(span.typeScore);

                //  map the name to its display name. if no mapping, we should get the same name back as its displayName
                MappedEntity mappedEntity = (ebook.nameToMappedEntity.get(canonicalizedname));
                if(mappedEntity==null){
                    //add this name as a mapped entity in the entiybook.
                    mappedEntity = new MappedEntity();
                    mappedEntity.setDisplayName(canonicalizedname);
                    mappedEntity.setEntityType(type);
                    mappedEntity.addAltNames(canonicalizedname);
                    ebook.nameToMappedEntity.put(canonicalizedname,mappedEntity);
                    Set<Document> docset=new LinkedHashSet<>();
                    docsetmap.put(mappedEntity,new Pair(score,docset));
                    docset.add(doc);//No doc exists already for this mappedntity
                }else{
                    //add it in the docset.//what about the score??? For now take the score as max of all scores..
                    Double oldscore = docsetmap.get(mappedEntity).first;
                    Double finalscore = Double.max(oldscore,score);
                    Set<Document> docset  = docsetmap.get(mappedEntity).second;
                    docset.add(doc);
                    docsetmap.put(mappedEntity,new Pair(finalscore,docset));
                }

            }
        }
        //fill cache summary for ebook in other fields of ebook.
        ebook.fillSummaryFields(docsetmap);
    }

    public void fillEntityBookFromText(String entityMerges, Short type,boolean recalculateCache) {
        BufferedReader br = new BufferedReader(new StringReader(entityMerges));


        try {
            EntityBook entityBook = EntityBook.readObjectFromStream(br,type);
            mTypeToEntityBook.put(type,entityBook);
            if(recalculateCache){
                Map<MappedEntity,Pair<Double,Set<Document>>> docsetmap = new LinkedHashMap<>();
                //iterate over lucene doc to recalculate the count and other summaries of the modified
                //fill cache summary for ebook in other fields of ebook.
                double theta = 0.001;

                for (Document doc : mArchive.getAllDocs())

                {
                    Span[] spansbody = getEntitiesInDocFromLucene(doc, true);
                    Span[] spans = getEntitiesInDocFromLucene(doc, false);
                    Span[] allspans = ArrayUtils.addAll(spans,spansbody);
                    Set<String> seenInThisDoc = new LinkedHashSet<>();

                    for (Span span : allspans) {
                        // bail out if not of entity type that we're looking for, or not enough confidence
                        if (span.type != type || span.typeScore < theta)
                            continue;
                        Double score = new Double(span.typeScore);
                        String name = span.getText();
                        String canonicalizedname = EntityBook.canonicalize(name);

                        //  map the name to its display name. if no mapping, we should get the same name back as its displayName
                        MappedEntity mappedEntity = (entityBook.nameToMappedEntity.get(canonicalizedname));
                        if(mappedEntity==null){
                            continue; //It implies that we have erased some names from the entitybook so no need to consider them.
                        }
                        //add this doc in the docsetmap for the mappedEntity.
                        Double oldscore= Double.valueOf(0);
                        if(docsetmap.get(mappedEntity)!=null)
                         oldscore = docsetmap.get(mappedEntity).first;
                        Double finalscore = Double.max(oldscore,score);
                        Set<Document> docset  = new LinkedHashSet<>();
                        if(docsetmap.get(mappedEntity)!=null)
                            docset=docsetmap.get(mappedEntity).second;
                        docset.add(doc);
                        docsetmap.put(mappedEntity,new Pair(finalscore,docset));

                   }
                }
                //fill cache summary for ebook in other fields of ebook.
                entityBook.fillSummaryFields(docsetmap);
            }

        } catch (IOException e) {
            e.printStackTrace();
            Util.print_exception("Unable to initialize the entitybook with different entity information",e,log);
        }


    }

    public Collection<MappedEntity> getEntitiesForName(String name){
        Set<MappedEntity> result= new LinkedHashSet<>();
        for(NEType.Type t: NEType.Type.values()) {
            EntityBook ebook = this.getEntityBookForType(t.getCode());
            MappedEntity res = ebook.nameToMappedEntity.get(EntityBook.canonicalize(name));
            if(res!=null)
                result.add(res);
        }
        return result;
    }

    public MappedEntity getEntityForNameAndType(String name, short type) {
        EntityBook ebook = this.getEntityBookForType(type);
        return ebook.nameToMappedEntity.get(EntityBook.canonicalize(name));
    }
    /* get the preferred display name for the given string */
    public String getDisplayName(String s, short type) {
        MappedEntity m = getEntityForNameAndType(s,type);
        if(m==null)
            return s;
        else
            return m.getDisplayName();
    }
/*
    public void initialize(short type, String text) {
        initialize(text);
        typeToEntityMapper.put(type, emft);
    }*/

    /* given this display name for a type, look up its alt names */
    public Set<String> getAltNamesForDisplayName(String displayName, short type) {
        MappedEntity m = getEntityForNameAndType(displayName,type);
        if(m==null)
            return null;
        else
            return m.getAltNames();
/*
        String cname = canonicalize(displayName);
        EntityMapperForType em = typeToEntityMapper.get(type);
        if (em == null)
            return null;

        Collection<MappedEntity> mes = em.cNameToMappedEntity.get(cname);
        if (mes == null || Util.nullOrEmpty(mes))
            return null;

        MappedEntity me = mes.iterator().next();
        if (Util.nullOrEmpty(me.displayName))
            return null;

        if (me.displayName.equals(displayName))
            return me.altNames;

        return null;*/
    }

    public Set<String> getAllEntities() {
        Set<String> result= new LinkedHashSet<>();
        for(NEType.Type t: NEType.Type.values()) {
            EntityBook ebook = this.getEntityBookForType(t.getCode());
            result.addAll(ebook.getAllEntities());

        }
        return result;
    }

    public Set<Pair<String, Pair<Pair<Date,Date>,Integer>>> getAllEntitiesSummary() {
        Set<Pair<String, Pair<Pair<Date,Date>,Integer>>> result = new LinkedHashSet<>();
        for(NEType.Type t: NEType.Type.values()) {
            EntityBook ebook = this.getEntityBookForType(t.getCode());
            ebook.summary_L1_entityCountMap.entrySet().forEach(s -> {
                result.add(new Pair(s.getKey().getDisplayName(), new Pair(new Pair(s.getValue().startDate, s.getValue().endDate),s.getValue().messages.size())));
            });
        }
        return result;


    }
}