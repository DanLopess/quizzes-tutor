package pt.ulisboa.tecnico.socialsoftware.tutor.course;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import pt.ulisboa.tecnico.socialsoftware.tutor.exceptions.TutorException;
import pt.ulisboa.tecnico.socialsoftware.tutor.user.User;
import pt.ulisboa.tecnico.socialsoftware.tutor.user.dto.StudentDto;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static pt.ulisboa.tecnico.socialsoftware.tutor.exceptions.ErrorMessage.COURSE_EXECUTION_NOT_FOUND;
import static pt.ulisboa.tecnico.socialsoftware.tutor.exceptions.ErrorMessage.INVALID_TYPE_FOR_COURSE;

@Service
public class CourseService {
    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseExecutionRepository courseExecutionRepository;

    @Retryable(
            value = { SQLException.class },
            backoff = @Backoff(delay = 5000))
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public List<CourseDto> getCourseExecutions(User.Role role) {
        return courseExecutionRepository.findAll().stream()
                .filter(courseExecution -> role.equals(User.Role.ADMIN) ||
                        (role.equals(User.Role.DEMO_ADMIN) && courseExecution.getCourse().getName().equals(Course.DEMO_COURSE)))
                .map(CourseDto::new)
                .sorted(Comparator
                        .comparing(CourseDto::getName)
                        .thenComparing(CourseDto::getAcademicTerm))
                .collect(Collectors.toList());
    }

    @Retryable(
            value = { SQLException.class },
            backoff = @Backoff(delay = 5000))
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public CourseDto createTecnicoCourseExecution(CourseDto courseDto) {
        courseDto.setCourseExecutionType(Course.Type.TECNICO);

        Course course = getCourse(courseDto);

        CourseExecution courseExecution = getCourseExecution(course, courseDto);

        courseExecution.setStatus(CourseExecution.Status.ACTIVE);
        return new CourseDto(courseExecution);
    }

    @Retryable(
            value = { SQLException.class },
            backoff = @Backoff(delay = 5000))
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void deactivateCourseExecution(int executionId) {
        CourseExecution courseExecution = courseExecutionRepository.findById(executionId).orElseThrow(() -> new TutorException(COURSE_EXECUTION_NOT_FOUND));
        courseExecution.setStatus(CourseExecution.Status.INACTIVE);
    }

    @Retryable(
            value = { SQLException.class },
            backoff = @Backoff(delay = 5000))
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public CourseDto createExternalCourseExecution(CourseDto courseDto) {
        courseDto.setCourseExecutionType(Course.Type.EXTERNAL);

        Course course = getCourse(courseDto);

        CourseExecution courseExecution = getCourseExecution(course, courseDto);

        courseExecution.setStatus(CourseExecution.Status.ACTIVE);
        return new CourseDto(courseExecution);
    }

    @Retryable(
            value = { SQLException.class },
            backoff = @Backoff(delay = 5000))
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public List<StudentDto> courseStudents(int executionId) {
        CourseExecution courseExecution = courseExecutionRepository.findById(executionId).orElse(null);
        if (courseExecution == null) {
            return new ArrayList<>();
        }
        return courseExecution.getUsers().stream()
                .filter(user -> user.getRole().equals(User.Role.STUDENT))
                .sorted(Comparator.comparing(User::getKey))
                .map(StudentDto::new)
                .collect(Collectors.toList());
    }

    @Retryable(
            value = { SQLException.class },
            backoff = @Backoff(delay = 5000))
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void removeCourseExecution(int courseExecutionId) {
        CourseExecution courseExecution = courseExecutionRepository.findById(courseExecutionId)
                .orElseThrow(() -> new TutorException(COURSE_EXECUTION_NOT_FOUND, courseExecutionId));

        courseExecution.delete();

        courseExecutionRepository.delete(courseExecution);
    }

    @Retryable(
            value = { SQLException.class },
            backoff = @Backoff(delay = 5000))
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public CourseDto getCourseExecutionById(int courseExecutionId) {
        return courseExecutionRepository.findById(courseExecutionId)
                .map(CourseDto::new)
                .orElseThrow(() -> new TutorException(COURSE_EXECUTION_NOT_FOUND, courseExecutionId));

    }

    private Course getCourse(CourseDto courseDto) {
        if (courseDto.getCourseType() == null)
            throw new TutorException(INVALID_TYPE_FOR_COURSE);

        return courseRepository.findByNameType(courseDto.getName(), courseDto.getCourseType().name())
                .orElseGet(() -> {
                    Course course = new Course(courseDto.getName(), courseDto.getCourseType());
                    courseRepository.save(course);
                    return course;
                });
    }

    private CourseExecution getCourseExecution(Course existingCourse, CourseDto courseDto) {
        return existingCourse.getCourseExecution(courseDto.getAcronym(), courseDto.getAcademicTerm(), courseDto.getCourseExecutionType())
                .orElseGet(() ->  {
                    CourseExecution courseExecution = new CourseExecution(existingCourse, courseDto.getAcronym(), courseDto.getAcademicTerm(), courseDto.getCourseExecutionType());
                    courseExecutionRepository.save(courseExecution);
                    return courseExecution;
                });
    }
}
