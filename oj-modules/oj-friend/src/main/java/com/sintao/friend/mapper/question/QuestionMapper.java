package com.sintao.friend.mapper.question;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sintao.friend.domain.question.Question;
import com.sintao.friend.domain.question.dto.QuestionQueryDTO;
import com.sintao.friend.domain.question.vo.QuestionVO;

import java.util.List;

public interface QuestionMapper extends BaseMapper<Question> {

    List<QuestionVO> selectQuestionList(QuestionQueryDTO questionQueryDTO);

}

