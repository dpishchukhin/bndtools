package name.neilbartlett.eclipse.bndtools.editor.model;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Map.Entry;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.osgi.framework.Constants;

import aQute.lib.osgi.Processor;
import aQute.libg.header.OSGiHeader;

/**
 * A model for a Bnd file. In the first iteration, use a simple Properties
 * object; this will need to be enhanced to additionally record formatting, e.g.
 * line breaks and empty lines, and comments.
 * 
 * @author Neil Bartlett
 */
public class BndEditModel {
	
	private static final String LIST_SEPARATOR = ",\\\n\t";
	private static final String ISO_8859_1 = "ISO-8859-1"; //$NON-NLS-1$
	
	private static final String[] KNOWN_PROPERTIES = new String[] {
		Constants.BUNDLE_SYMBOLICNAME,
		Constants.BUNDLE_VERSION,
		Constants.BUNDLE_ACTIVATOR,
		Constants.EXPORT_PACKAGE,
		Constants.IMPORT_PACKAGE,
		aQute.lib.osgi.Constants.PRIVATE_PACKAGE,
		aQute.lib.osgi.Constants.SOURCES,
		aQute.lib.osgi.Constants.VERSIONPOLICY,
		aQute.lib.osgi.Constants.SERVICE_COMPONENT,
	};
	
	private interface Converter<R,T> {
		R convert(T input) throws IllegalArgumentException;
	}
	
	private interface Formatter<T> {
		String format(T object);
	}
	
	private final PropertyChangeSupport propChangeSupport = new PropertyChangeSupport(this);
	private final Properties properties = new Properties();;
	private final Map<String, Object> objectProperties = new HashMap<String, Object>();
	private final Map<String, String> changesToSave = new HashMap<String, String>();
	
	public void loadFrom(IDocument document) throws IOException {
		// Clear and load
		properties.clear();
		InputStream stream = new ByteArrayInputStream(document.get().getBytes(ISO_8859_1));
		properties.load(stream);
		objectProperties.clear();
		changesToSave.clear();
		
		// Fire property changes on all known property names
		for (String prop : KNOWN_PROPERTIES) {
			// null values for old and new forced the change to be fired
			propChangeSupport.firePropertyChange(prop, null, null);
		}
	}
	
	public void saveChangesTo(IDocument document) {
		for(Iterator<Entry<String,String>> iter = changesToSave.entrySet().iterator(); iter.hasNext(); ) {
			Entry<String, String> entry = iter.next();
			iter.remove();
			
			String propertyName = entry.getKey();
			String stringValue = entry.getValue();
			
			updateDocument(document, propertyName, stringValue);
		}
	}
	
	private static IRegion findEntry(IDocument document, String name) throws BadLocationException {
		int lineCount = document.getNumberOfLines();
		
		int entryStart = -1;
		int entryLength = 0;
		
		for(int i=0; i<lineCount; i++) {
			IRegion lineRegion = document.getLineInformation(i);
			String line = document.get(lineRegion.getOffset(), lineRegion.getLength());
			if(line.startsWith(name)) {
				entryStart = lineRegion.getOffset();
				entryLength = lineRegion.getLength();
				
				// Handle continuation lines, where the current line ends with a blackslash.
				while(document.getChar(lineRegion.getOffset() + lineRegion.getLength() - 1) == '\\') {
					if(++i >= lineCount) {
						break;
					}
					lineRegion = document.getLineInformation(i);
					entryLength += lineRegion.getLength() + 1; // Extra 1 is required for the newline
				}
				
				return new Region(entryStart, entryLength);
			}
		}
		
		return null;
	}
	
	private static void updateDocument(IDocument document, String name, String value) {
		String newEntry;
		if(value != null) {
			StringBuilder buffer = new StringBuilder();
			buffer.append(name).append(": ").append(value);
			newEntry = buffer.toString();
		} else {
			newEntry = "";
		}
		
		try {
			IRegion region = findEntry(document, name);
			if(region != null) {
				// Replace an existing entry
				int offset = region.getOffset();
				int length = region.getLength();
				
				// If the replacement is empty, remove one extra character to the right, i.e. the following newline,
				// unless this would take us past the end of the document
				if(newEntry.length() == 0 && offset + length + 1 < document.getLength()) {
					length++;
				}
				document.replace(offset, length, newEntry);
			} else if(newEntry.length() > 0) {
				// This is a new entry, put it at the end of the file
				
				// Does the last line of the document have a newline? If not,
				// we need to add one.
				if(document.getLength() > 0 && document.getChar(document.getLength() - 1) != '\n')
					newEntry = "\n" + newEntry;
				document.replace(document.getLength(), 0, newEntry);
			}
		} catch (BadLocationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	private String doGetString(String name) {
		String result;
		if(objectProperties.containsKey(name)) {
			result = (String) objectProperties.get(name);
		} else {
			if(properties.containsKey(name)) {
				result = properties.getProperty(name);
				objectProperties.put(name, result);
			} else {
				result = null;
			}
		}
		return result;
	}
	private <R> R doGetObject(String name, Converter<? extends R, ? super String> converter) {
		R result;
		if(objectProperties.containsKey(name)) {
			@SuppressWarnings("unchecked")
			R temp = (R) objectProperties.get(name);
			result = temp;
		} else {
			if(properties.containsKey(name)) {
				result = converter.convert(properties.getProperty(name));
				objectProperties.put(name, result);
			} else {
				result = null;
			}
		}
		return result;
	}
	private void doSetObject(String name, Object oldValue, Object newValue, String formattedString) {
		objectProperties.put(name, newValue);
		changesToSave.put(name, formattedString);
		propChangeSupport.firePropertyChange(name, oldValue, newValue);
	}
	
	public String getBundleSymbolicName() {
		return doGetString(Constants.BUNDLE_SYMBOLICNAME);
	}
	
	public void setBundleSymbolicName(String bundleSymbolicName) {
		doSetObject(Constants.BUNDLE_SYMBOLICNAME, getBundleSymbolicName(), bundleSymbolicName, bundleSymbolicName); 
	}
	
	public String getBundleVersionString() {
		return doGetString(Constants.BUNDLE_VERSION); 
	}
	
	public void setBundleVersion(String bundleVersion) {
		doSetObject(Constants.BUNDLE_VERSION, getBundleVersionString(), bundleVersion, bundleVersion);
	}
	
	public String getBundleActivator() {
		return doGetString(Constants.BUNDLE_ACTIVATOR); 
	}
	
	public void setBundleActivator(String bundleActivator) {
		doSetObject(Constants.BUNDLE_ACTIVATOR, getBundleActivator(), bundleActivator, bundleActivator);
	}
	
	public void setIncludeSources(boolean includeSources) {
		boolean oldValue = isIncludeSources();
		String formattedString = includeSources ? Boolean.TRUE.toString() : null;
		doSetObject(aQute.lib.osgi.Constants.SOURCES, oldValue, includeSources, formattedString);
	}
	
	public boolean isIncludeSources() {
		Boolean objValue = doGetObject(aQute.lib.osgi.Constants.SOURCES, new Converter<Boolean,String>() {
			public Boolean convert(String string) throws IllegalArgumentException {
				return Boolean.valueOf(string);
			}
		});
		return objValue != null ? objValue.booleanValue() : false;
	}
	
	public VersionPolicy getVersionPolicy() throws IllegalArgumentException {
		return doGetObject(aQute.lib.osgi.Constants.VERSIONPOLICY, new Converter<VersionPolicy,String>() {
			public VersionPolicy convert(String string) throws IllegalArgumentException {
				return VersionPolicy.parse(string);
			}
		});
	}
	
	public void setVersionPolicy(VersionPolicy versionPolicy) {
		String string = versionPolicy != null ? versionPolicy.toString() : null;
		VersionPolicy oldValue;
		try {
			oldValue = getVersionPolicy();
		} catch (IllegalArgumentException e) {
			oldValue = null;
		}
		doSetObject(aQute.lib.osgi.Constants.VERSIONPOLICY, oldValue, versionPolicy, string); 
	}
	/**
	 * Get the exported packages; the returned collection will have been newly
	 * allocated, and may be manipulated by clients without fear of affecting
	 * other clients.
	 * 
	 * @return A new collection containing the exported packages of the model.
	 */
	public Collection<ExportedPackage> getExportedPackages() {
		return doGetObject(Constants.EXPORT_PACKAGE, new Converter<Collection<ExportedPackage>,String>() {
			public Collection<ExportedPackage> convert(String string) throws IllegalArgumentException {
				List<ExportedPackage> result = new LinkedList<ExportedPackage>();
				Map<String, Map<String, String>> header = OSGiHeader.parseHeader(string, null);
				for (Entry<String, Map<String,String>> entry : header.entrySet()) {
					String packageName = entry.getKey();
					String version = null;
					Map<String, String> attribs = entry.getValue();
					if(attribs != null) {
						version = attribs.get(Constants.VERSION_ATTRIBUTE);
					}
					
					result.add(new ExportedPackage(packageName, version));
				}
				return result;
			}
		});
	}
	public Collection<String> getPrivatePackages() {
		return doGetObject(aQute.lib.osgi.Constants.PRIVATE_PACKAGE, new Converter<Collection<String>,String>() {
			public Collection<String> convert(String string) {
				List<String> packages = new LinkedList<String>();
				Map<String, Map<String, String>> header = OSGiHeader.parseHeader(string);
				for(Entry<String, Map<String,String>> entry : header.entrySet()) {
					String packageName = entry.getKey();
					packages.add(packageName);
				}
				return packages;
			}
		});
	}
	public void setExportedPackages(Collection<? extends ExportedPackage> packages) {
		Collection<ExportedPackage> oldPackages = getExportedPackages();
		StringBuilder buffer = new StringBuilder();
		
		if(packages == null || packages.isEmpty()) {
			doSetObject(Constants.EXPORT_PACKAGE, oldPackages, null, null);
		} else {
			for(Iterator<? extends ExportedPackage> iter = packages.iterator(); iter.hasNext(); ) {
				ExportedPackage pkg = iter.next();
				buffer.append(pkg.getPackageName());
				
				if(pkg.getVersion() != null)
					buffer.append(";version=\"").append(pkg.getVersion()).append("\"");
				
				if(iter.hasNext())
					buffer.append(LIST_SEPARATOR);
			}
			doSetObject(Constants.EXPORT_PACKAGE, oldPackages, packages, buffer.toString());
		}
	}
	public void addExportedPackage(ExportedPackage export) {
		Collection<ExportedPackage> exports = getExportedPackages();
		exports = (exports == null) ? new ArrayList<ExportedPackage>() : new ArrayList<ExportedPackage>(exports);
		exports.add(export);
		setExportedPackages(exports);
	}
	public void setPrivatePackages(Collection<? extends String> packages) {
		Collection<String> oldPackages = getPrivatePackages();
		
		StringBuilder buffer = new StringBuilder();
		if(packages == null || packages.isEmpty()) {
			doSetObject(aQute.lib.osgi.Constants.PRIVATE_PACKAGE, oldPackages, null, null);
		} else {
			for(Iterator<? extends String> iter = packages.iterator(); iter.hasNext(); ) {
				String pkg = iter.next();
				buffer.append(pkg);
				if(iter.hasNext())
					buffer.append(LIST_SEPARATOR);
			}
			doSetObject(aQute.lib.osgi.Constants.PRIVATE_PACKAGE, oldPackages, packages, buffer.toString());
		}
	}
	public void addPrivatePackage(String packageName) {
		Collection<String> packages = getPrivatePackages();
		if(packages == null)
			packages = new ArrayList<String>();
		else
			packages = new ArrayList<String>(packages);
		packages.add(packageName);
		setPrivatePackages(packages);
	}
	<R> List<R> getHeaderClauses(String name, final Converter<? extends R, Entry<String, Map<String,String>>> converter) {
		return doGetObject(name, new Converter<List<R>,String>() {
			public List<R> convert(String string) throws IllegalArgumentException {
				List<R> result = new ArrayList<R>();
				Processor processor = new Processor(properties);
				Map<String, Map<String, String>> scHeader = processor.parseHeader(string);
				for (Entry<String, Map<String, String>> entry : scHeader.entrySet()) {
					result.add(converter.convert(entry));
				}
				return result;
			}
		});
	}
	public List<ServiceComponent> getServiceComponents() {
		return getHeaderClauses(aQute.lib.osgi.Constants.SERVICE_COMPONENT, new Converter<ServiceComponent, Entry<String,Map<String,String>>>() {
			public ServiceComponent convert(Entry<String, Map<String, String>> input) throws IllegalArgumentException {
				return new ServiceComponent(input.getKey(), input.getValue());
			}
		});
	}
	public List<ImportPattern> getImportPatterns() {
		return getHeaderClauses(Constants.IMPORT_PACKAGE, new Converter<ImportPattern, Entry<String,Map<String,String>>>() {
			public ImportPattern convert(Entry<String, Map<String, String>> input) throws IllegalArgumentException {
				return new ImportPattern(input.getKey(), input.getValue());
			}
		});
	}
	private void doSetClauseList(String name, Collection<? extends HeaderClause> oldValue, Collection<? extends HeaderClause> newValue) {
		StringBuilder buffer = new StringBuilder();
		if(newValue == null || newValue.isEmpty()) {
			doSetObject(name, oldValue, null, null);
		} else {
			for(Iterator<? extends HeaderClause> iter = newValue.iterator(); iter.hasNext(); ) {
				HeaderClause clause = iter.next();
				clause.formatTo(buffer);
				if(iter.hasNext())
					buffer.append(LIST_SEPARATOR);
			}
			doSetObject(name, oldValue, newValue, buffer.toString());
		}
	}
	public void setServiceComponents(Collection<? extends ServiceComponent> components) {
		List<ServiceComponent> oldValue = getServiceComponents();
		doSetClauseList(aQute.lib.osgi.Constants.SERVICE_COMPONENT, oldValue, components);
	}
	public void setImportPatterns(Collection<? extends ImportPattern> patterns) {
		List<ImportPattern> oldValue = getImportPatterns();
		doSetClauseList(Constants.IMPORT_PACKAGE, oldValue, patterns);
	}
	public boolean isIncludedPackage(String packageName) {
		final Collection<String> privatePackages = getPrivatePackages();
		if(privatePackages != null) {
			if(privatePackages.contains(packageName))
				return true;
		}
		final Collection<ExportedPackage> exportedPackages = getExportedPackages();
		if(exportedPackages != null) {
			for (ExportedPackage pkg : exportedPackages) {
				if(packageName.equals(pkg.getPackageName())) {
					return true;
				}
			}
		}
		return false;
	}
	
	// BEGIN: PropertyChangeSupport delegate methods

	public void addPropertyChangeListener(PropertyChangeListener listener) {
		propChangeSupport.addPropertyChangeListener(listener);
	}

	public void addPropertyChangeListener(String propertyName,
			PropertyChangeListener listener) {
		propChangeSupport.addPropertyChangeListener(propertyName, listener);
	}

	public void removePropertyChangeListener(PropertyChangeListener listener) {
		propChangeSupport.removePropertyChangeListener(listener);
	}

	public void removePropertyChangeListener(String propertyName,
			PropertyChangeListener listener) {
		propChangeSupport.removePropertyChangeListener(propertyName, listener);
	}

	// END: PropertyChangeSupport delegate methods
	
}
