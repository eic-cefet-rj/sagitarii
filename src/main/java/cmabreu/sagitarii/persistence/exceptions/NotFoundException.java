package cmabreu.sagitarii.persistence.exceptions;

public class NotFoundException extends PersistenceException {
	private static final long serialVersionUID = 1L;

	public NotFoundException(String message){
		super(message);
	}
	
}
