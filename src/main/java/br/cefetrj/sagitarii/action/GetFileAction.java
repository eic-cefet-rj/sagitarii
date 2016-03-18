
package br.cefetrj.sagitarii.action;

import java.io.File;
import java.io.FileInputStream;

import org.apache.struts2.convention.annotation.Action;
import org.apache.struts2.convention.annotation.ParentPackage;
import org.apache.struts2.convention.annotation.Result;

import br.cefetrj.sagitarii.misc.PathFinder;
import br.cefetrj.sagitarii.persistence.services.FileService;

@Action(value="getFile", results= {  
	    @Result(name="ok", type="stream", params = {
                "contentType", "application/octet-stream",
                "inputName", "fileInputStream",
                "contentDisposition", "filename=\"${fileName}\"",
                "bufferSize", "1024"
        }) }
)   

@ParentPackage("default")
public class GetFileAction extends BasicActionClass {
	private String fileName;
	private Integer idFile;
	private FileInputStream fileInputStream;
	private String macAddress;
	
	public String execute () {
		br.cefetrj.sagitarii.persistence.entity.File file = null;
		
		String gz = "";
		if ( (macAddress == null) || (macAddress.equals("") ) ) {
			gz = ".gz";
		}
		
		try {
			FileService fs = new FileService();
			if ( (idFile != null) && ( idFile > -1 ) ) {
				file = fs.getFile( idFile );
				fileName = file.getFileName() + gz;
				String theFile = PathFinder.getInstance().getPath() + "/storage/" + file.getFilePath() + "/" + fileName;
				File fil = new File( theFile );
				fileInputStream = new FileInputStream( fil );
			}
		} catch ( Exception e ) {
            e.printStackTrace();
		}
		
		return "ok";
	}

	
	public void setIdFile(Integer idFile) {
		this.idFile = idFile;
	}
	
	public String getFileName() {
		return fileName;
	}
	
	
	public FileInputStream getFileInputStream() {
		return fileInputStream;
	}
	
	public void setFileName(String fileName) {
		this.fileName = fileName;
	}
	
	public void setMacAddress(String macAddress) {
		this.macAddress = macAddress;
	}

}